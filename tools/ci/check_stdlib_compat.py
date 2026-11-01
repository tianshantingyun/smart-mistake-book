#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""kotlin-stdlib 2.3.21 降级兼容门：全解析图 stdlib 成员引用静态扫描（KD-73）。

消灭的具体失败：运行时 kotlin-stdlib 被 ``eachDependency + useVersion`` 强制压回
2.3.21（根 ``build.gradle.kts``，上游 quickjs-kt-android 1.0.15 的 POM 要求
2.4.10）。若解析图中任何 jar 引用了 2.3.21 没有的 stdlib 类/成员，编译期不报错、
只在运行期以 ``NoClassDefFoundError`` / ``NoSuchMethodError`` 崩。批次 30 T7 曾
自述做过一次全图扫描（"103,041 class / 138,225 条引用全部可解析"），但扫描器、
逐构件清单与结果均未落仓，不可复跑 → UNVERIFIED（KD-73 批次 31/33/34/35 各段
复核一致）。本脚本把该证据钉成可复跑的门。

语义（对应 KD-73 Reopen condition 的静态半边"完整解析图 unresolved=0"）：

1. **解析图**：以盘上全部 ``gradle.lockfile`` 为图（与批次 30 同一来源；这些锁
   目前未跟踪、生成于他线构建改动之上——图身份随锁变化，脚本每次运行都打印图
   来源，结果解释必须携带该口径）。没有锁文件时只提示并放行（同
   ``check_lockfiles.py`` 的 skip 语义：没有图 ≠ 兼容已证明）。
2. **构件**：每个锁坐标在 Gradle 缓存（项目 ``.gradle/caches`` 与
   ``~/.gradle/caches``）里定位 ``.jar``/``.aar``；androidx KMP 重定向坐标
   （``foo`` → ``foo-android``/``foo-jvm``）按兄弟目录解析。定位不到的坐标计入
   "未扫描"清单（报告可见，不算 unresolved——但 OK 消息会声明扫描覆盖率）。
3. **类解析**：逐 class 解析常量池，提取 owner 为 ``kotlin/*`` 的
   Fieldref/Methodref/InterfaceMethodref（java/* 与其它 owner 不是本门对象）。
4. **索引**：
   - 主索引 = 运行时 stdlib（catalog ``kotlin``，当前 2.3.21）的全部 ``kotlin/*`` 类；
   - 走出链索引 = ``android.jar``（compileSdk 平台）的 ``java/*``/``javax/*`` 类，
     用于 stdlib 类经父类链走出 stdlib 的成员（如 ``kotlin/text/StringBuilder``
     继承 ``java.lang.StringBuilder``）；
   - 二级索引 = 图内其它构件的 ``kotlin/*`` 类（kotlin-reflect、
     kotlin-stdlib-jdk8 等）：owner 不在主索引、或成员为图内其它 jar 真实声明时，
     记 ``resolved-secondary``（信息级，逐 jar 归因）；
   - 对照索引 = catalog ``quickjs-kotlin-stdlib``（当前 2.4.10）的 stdlib：仅用于
     把失败分级为"降级丢类"（类在 2.4.10 有、2.3.21 没有）与"2.4-only 成员"。
5. **判定**：exit 0 当且仅当 ``fail-class`` 与 ``fail-member`` 均为 0；
   ``non-stdlib``/``resolved-secondary``/``unscannable`` 只是报告桶，不进判定。
6. **元数据腿**（信息级）：逐坐标读缓存内 ``.pom``/``.module``，列出声明
   kotlin-stdlib ≠ 运行时版本的坐标（复核"只有 quickjs 声明 2.4.10"）。

不运行 Gradle、不联网、不写盘；只读锁文件与缓存。每步只读，退出码
0 = 通过/无图，1 = 发现不兼容引用，2 = 环境不满足（android.jar / 运行时 stdlib 缺失）。
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import struct
import sys
import zipfile
from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
CACHE_ROOTS = (ROOT / ".gradle" / "caches", Path.home() / ".gradle" / "caches")
STDLIB_GROUP = "org.jetbrains.kotlin"
STDLIB_ARTIFACT = "kotlin-stdlib"
EXCLUDED_PARTS = {".git", ".worktrees", ".gradle", "build", "tmp", ".zcode", "node_modules"}
KOTLIN_PREFIX = "kotlin/"
JDK_PREFIXES = ("java/", "javax/")
ARTIFACT_SUFFIXES = (".jar", ".aar")
AAR_CLASS_JAR_RE = re.compile(r"classes\d*\.jar")
POM_STDLIB_RE = re.compile(
    r"<artifactId>kotlin-stdlib(?:-\w+)?</artifactId>\s*<version>([^<]+)</version>"
)


# ---------------------------------------------------------------- 类文件解析


@dataclass(frozen=True)
class ClassInfo:
    name: str
    super_name: str | None
    interfaces: tuple[str, ...]
    members: frozenset[tuple[str, str]]


@dataclass(frozen=True)
class Ref:
    owner: str
    name: str
    desc: str

    def as_text(self) -> str:
        return f"{self.owner}.{self.name}{self.desc}"


@dataclass(frozen=True)
class RefVerdict:
    ref: Ref
    status: str  # resolved / resolved-secondary / fail-class / fail-member / non-stdlib
    where: str | None
    note: str | None


def parse_class(data: bytes) -> tuple[ClassInfo | None, list[Ref]]:
    """解析单个 .class：返回 (类信息, kotlin/* 成员引用列表)。

    非 class 文件 / 结构损坏 → ValueError；module-info / package-info → (None, [])。
    """
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a Java class file")
    (cp_count,) = struct.unpack_from(">H", data, 8)
    cp: list[tuple | None] = [None] * cp_count
    offset = 10
    index = 1
    while index < cp_count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            (length,) = struct.unpack_from(">H", data, offset)
            offset += 2
            cp[index] = (1, bytes(data[offset : offset + length]))
            offset += length
        elif tag in (3, 4):
            cp[index] = (tag, struct.unpack_from(">I", data, offset)[0])
            offset += 4
        elif tag in (5, 6):
            cp[index] = (tag, struct.unpack_from(">Q", data, offset)[0])
            offset += 8
            index += 1  # Long/Double 占两个常量池槽位
        elif tag in (7, 8, 16, 19, 20):
            cp[index] = (tag, struct.unpack_from(">H", data, offset)[0])
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[index] = (tag, struct.unpack_from(">HH", data, offset))
            offset += 4
        elif tag == 15:
            cp[index] = (tag, (data[offset], struct.unpack_from(">H", data, offset + 1)[0]))
            offset += 3
        else:
            raise ValueError(f"unknown constant-pool tag {tag}")
        index += 1

    def utf8(idx: int) -> str:
        entry = cp[idx]
        assert entry is not None and entry[0] == 1
        return entry[1].decode("utf-8", "replace")

    def classname(idx: int) -> str | None:
        if idx == 0:
            return None
        entry = cp[idx]
        assert entry is not None and entry[0] == 7
        return utf8(entry[1])

    (access, this_idx, super_idx) = struct.unpack_from(">HHH", data, offset)
    offset += 6
    this_name = classname(this_idx)
    if this_name is None or this_name in ("module-info", "package-info"):
        return None, []
    super_name = classname(super_idx)
    (ifc_count,) = struct.unpack_from(">H", data, offset)
    offset += 2
    interfaces: list[str] = []
    for _ in range(ifc_count):
        (idx,) = struct.unpack_from(">H", data, offset)
        offset += 2
        name = classname(idx)
        if name:
            interfaces.append(name)

    def read_members(count: int) -> set[tuple[str, str]]:
        nonlocal offset
        members: set[tuple[str, str]] = set()
        for _ in range(count):
            offset += 2  # access_flags
            (name_idx, desc_idx, attr_count) = struct.unpack_from(">HHH", data, offset)
            offset += 6
            members.add((utf8(name_idx), utf8(desc_idx)))
            for _ in range(attr_count):
                offset += 2  # attribute_name_index
                (attr_len,) = struct.unpack_from(">I", data, offset)
                offset += 4 + attr_len
        return members

    (fields_count,) = struct.unpack_from(">H", data, offset)
    offset += 2
    fields = read_members(fields_count)
    (methods_count,) = struct.unpack_from(">H", data, offset)
    offset += 2
    methods = read_members(methods_count)

    info = ClassInfo(this_name, super_name, tuple(interfaces), frozenset(fields | methods))
    refs: list[Ref] = []
    for idx in range(1, cp_count):
        entry = cp[idx]
        if entry is None or entry[0] not in (9, 10, 11):
            continue
        class_idx, nat_idx = entry[1]
        owner = classname(class_idx)
        if owner and owner.startswith(KOTLIN_PREFIX):
            name_idx, desc_idx = cp[nat_idx][1]
            refs.append(Ref(owner, utf8(name_idx), utf8(desc_idx)))
    return info, refs


def _iter_class_entries(
    container: bytes,
    source: str,
    prefixes: Iterable[str] = (KOTLIN_PREFIX,),
) -> Iterator[tuple[str, ClassInfo | None, list[Ref], str | None]]:
    """遍历 zip 容器（jar/AAR 内层 jar）中的 .class 条目。

    产出 (entry, info, refs, error)。多版本 jar 的 ``META-INF/versions/*`` 与基础
    条目都会被扫引用；索引以先出现的基础条目为准（本图内 stdlib 相关 jar 均非
    多版本 jar，该规则只为确定性）。
    """
    prefix_tuple = tuple(prefixes)
    try:
        archive = zipfile.ZipFile(io.BytesIO(container))
    except zipfile.BadZipFile as error:
        yield source, None, [], f"bad zip: {error}"
        return
    with archive:
        for entry in archive.infolist():
            if entry.is_dir() or not entry.filename.endswith(".class"):
                continue
            data = archive.read(entry)
            try:
                info, refs = parse_class(data)
            except (ValueError, struct.error, AssertionError, IndexError) as error:
                yield f"{source}!{entry.filename}", None, [], f"unparseable class: {error}"
                continue
            if info is not None and not info.name.startswith(prefix_tuple):
                info = None
            refs = [ref for ref in refs if ref.owner.startswith(prefix_tuple)]
            yield f"{source}!{entry.filename}", info, refs, None


def build_class_index(
    container: bytes,
    prefixes: Iterable[str] = (KOTLIN_PREFIX,),
) -> dict[str, ClassInfo]:
    """从 zip 容器构建 ``类名 -> ClassInfo`` 索引（仅 ``prefixes`` 前缀的类）。"""
    index: dict[str, ClassInfo] = {}
    for _entry, info, _refs, _error in _iter_class_entries(container, "", prefixes):
        if info is not None:
            index.setdefault(info.name, info)
    return index


def index_and_refs_from_artifact(
    path: Path,
    prefixes: Iterable[str] = (KOTLIN_PREFIX,),
) -> tuple[dict[str, ClassInfo], list[Ref], list[str]]:
    """从 .jar/.aar 构建索引并收集引用；AAR 取内层 ``classesN.jar``。"""
    index: dict[str, ClassInfo] = {}
    refs: list[Ref] = []
    problems: list[str] = []
    data = path.read_bytes()
    containers: list[tuple[str, bytes]] = []
    if path.suffix == ".aar":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as outer:
                class_jars = [n for n in outer.namelist() if AAR_CLASS_JAR_RE.fullmatch(n)]
                if not class_jars:
                    problems.append(f"{path}: AAR has no classes.jar (native/metadata-only?)")
                for name in sorted(class_jars):
                    containers.append((f"{path}!{name}", outer.read(name)))
        except zipfile.BadZipFile as error:
            problems.append(f"{path}: bad AAR zip: {error}")
    else:
        containers.append((str(path), data))
    for source, container in containers:
        for _entry, info, class_refs, error in _iter_class_entries(container, source, prefixes):
            if error:
                problems.append(error)
                continue
            if info is not None:
                index.setdefault(info.name, info)
            refs.extend(class_refs)
    return index, refs, problems


# ---------------------------------------------------------------- 解析图


def discover_lockfiles(root: Path) -> list[Path]:
    return sorted(
        path
        for path in root.glob("**/gradle.lockfile")
        if not any(part in EXCLUDED_PARTS for part in path.parts)
    )


def parse_lock_text(text: str) -> set[tuple[str, str, str]]:
    """锁行 ``group:artifact:version=config,...`` → 坐标集合（注释/空行跳过）。"""
    coordinates: set[tuple[str, str, str]] = set()
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        left, _, _right = line.partition("=")
        parts = left.split(":")
        if len(parts) >= 3 and all(parts[:3]):
            coordinates.add((parts[0], parts[1], parts[2]))
    return coordinates


def candidate_modules(artifact: str) -> tuple[str, ...]:
    """androidx/JB KMP 重定向：锁里的 ``foo`` 常落在 ``foo-android``/``foo-jvm``/``foo-desktop`` 构件上。"""
    return (artifact, artifact + "-android", artifact + "-jvm", artifact + "-desktop")


def find_artifact_files(
    coordinate: tuple[str, str, str],
    cache_roots: Iterable[Path] = CACHE_ROOTS,
) -> list[Path]:
    group, artifact, version = coordinate
    found: dict[str, Path] = {}
    for cache in cache_roots:
        base = cache / "modules-2" / "files-2.1" / group
        if not base.is_dir():
            continue
        for module in candidate_modules(artifact):
            version_dir = base / module / version
            if not version_dir.is_dir():
                continue
            for path in version_dir.rglob("*"):
                if path.is_file() and path.suffix in ARTIFACT_SUFFIXES:
                    try:
                        size = path.stat().st_size
                    except OSError:
                        continue
                    found.setdefault(f"{module}/{version}/{path.name}/{size}", path)
    return sorted(found.values(), key=str)


def find_metadata_files(
    coordinate: tuple[str, str, str],
    cache_roots: Iterable[Path] = CACHE_ROOTS,
) -> list[Path]:
    group, artifact, version = coordinate
    found: dict[str, Path] = {}
    for cache in cache_roots:
        base = cache / "modules-2" / "files-2.1" / group
        for module in candidate_modules(artifact):
            version_dir = base / module / version
            if not version_dir.is_dir():
                continue
            for path in version_dir.rglob("*"):
                if path.is_file() and path.suffix in (".pom", ".module"):
                    found.setdefault(f"{module}/{version}/{path.name}", path)
    return sorted(found.values(), key=str)


def find_cached_stdlib_jar(version: str, cache_roots: Iterable[Path] = CACHE_ROOTS) -> list[Path]:
    return find_artifact_files((STDLIB_GROUP, STDLIB_ARTIFACT, version), cache_roots)


def declared_stdlib_requirements(pom_or_module: Path) -> set[str]:
    """从 .pom / .module 提取声明的 kotlin-stdlib 版本（best-effort，信息级）。"""
    try:
        text = pom_or_module.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return set()
    versions: set[str] = set()
    if pom_or_module.suffix == ".pom":
        versions.update(POM_STDLIB_RE.findall(text))
        return versions
    try:
        metadata = json.loads(text)
    except json.JSONDecodeError:
        return versions
    for variant in metadata.get("variants", []):
        for dependency in variant.get("dependencies", []):
            if dependency.get("group") == STDLIB_GROUP and dependency.get("module", "").startswith(
                STDLIB_ARTIFACT
            ):
                version_block = dependency.get("version") or {}
                for key in ("requires", "prefers", "strictly"):
                    value = version_block.get(key)
                    if isinstance(value, str):
                        versions.add(value)
    return versions


# ---------------------------------------------------------------- 引用解析


def _walk_chain(
    start: str,
    name: str,
    desc: str,
    indexes: Iterable[dict[str, ClassInfo]],
) -> str | None:
    """沿父类/接口链查 ``(name, desc)``；跨索引（stdlib→android.jar）继续走。"""
    member = (name, desc)
    seen: set[str] = set()
    stack = [start]
    while stack:
        cls = stack.pop()
        if cls in seen:
            continue
        seen.add(cls)
        info: ClassInfo | None = None
        for lookup in indexes:
            info = lookup.get(cls)
            if info is not None:
                break
        if info is None:
            continue
        if member in info.members:
            return cls
        if info.super_name:
            stack.append(info.super_name)
        stack.extend(info.interfaces)
    return None


def resolve_ref(
    ref: Ref,
    *,
    primary: dict[str, ClassInfo],
    android: dict[str, ClassInfo],
    secondary: dict[str, ClassInfo],
    stdlib24: dict[str, ClassInfo] | None,
) -> RefVerdict:
    owner = ref.owner
    secondary_info = secondary.get(owner)
    if owner not in primary:
        if stdlib24 is not None and owner in stdlib24:
            return RefVerdict(
                ref,
                "fail-class",
                None,
                "class exists in stdlib 2.4.10 but not in runtime stdlib 2.3.21 "
                "(downgrade loses the class"
                + ("; another graph artifact re-declares it — shadow, not stdlib" if secondary_info else "")
                + ")",
            )
        if secondary_info and (ref.name, ref.desc) in secondary_info.members:
            return RefVerdict(ref, "resolved-secondary", owner, "declared by another graph artifact")
        return RefVerdict(ref, "non-stdlib", None, "owner class not in runtime stdlib (owned elsewhere)")
    found = _walk_chain(owner, ref.name, ref.desc, (primary, android))
    if found:
        return RefVerdict(ref, "resolved", found, None)
    if stdlib24 is not None:
        found24 = _walk_chain(owner, ref.name, ref.desc, (stdlib24, android))
        if found24:
            # 2.4-only 成员：图内其它构件的同名遮蔽不能掩盖降级丢失。
            return RefVerdict(ref, "fail-member", None, f"member available in stdlib 2.4.10 at {found24}")
    if secondary_info and (ref.name, ref.desc) in secondary_info.members:
        return RefVerdict(ref, "resolved-secondary", owner, "declared by another graph artifact")
    note = None
    if stdlib24 is None:
        note = "upstream stdlib jar not available for 2.4.10 comparison"
    return RefVerdict(ref, "fail-member", None, note)


def evaluate_verdicts(verdicts: Iterable[RefVerdict]) -> list[str]:
    errors: list[str] = []
    for verdict in verdicts:
        if verdict.status == "fail-class":
            errors.append(
                f"fail-class: {verdict.ref.as_text()} — stdlib 2.3.21 缺少该类"
                f"（{verdict.note or ''}）"
            )
        elif verdict.status == "fail-member":
            errors.append(
                f"fail-member: {verdict.ref.as_text()} — 运行时 stdlib 2.3.21 链上不可解析"
                f"{'（' + verdict.note + '）' if verdict.note else ''}"
            )
    return errors


# ---------------------------------------------------------------- 编排


def catalog_version(name: str) -> str:
    match = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', CATALOG.read_text(encoding="utf-8"), re.MULTILINE)
    if not match:
        raise SystemExit(f"catalog entry not found: {name}")
    return match.group(1)


def locate_android_jar(explicit: str | None) -> Path:
    if explicit:
        path = Path(explicit)
        if not path.is_file():
            raise SystemExit(f"--android-jar not found: {path}")
        return path
    sdk_candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        "C:/Android/Sdk",
        str(Path.home() / "AppData" / "Local" / "Android" / "Sdk"),
    ]
    for sdk in sdk_candidates:
        if not sdk:
            continue
        platforms = Path(sdk) / "platforms"
        if not platforms.is_dir():
            continue
        jars = sorted(platforms.glob("android-*/android.jar"), key=lambda p: p.parent.name)
        if jars:
            return jars[-1]  # 版本号字典序最大 ≈ 最新平台
    raise SystemExit("no android.jar under any SDK platforms directory; pass --android-jar")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--android-jar", help="override android.jar used for java/* chain walk-out")
    args = parser.parse_args(argv)

    runtime_stdlib = catalog_version("kotlin")
    upstream_stdlib = catalog_version("quickjs-kotlin-stdlib")

    lockfiles = discover_lockfiles(ROOT)
    if not lockfiles:
        print("SKIP: no gradle.lockfile on disk — no resolution graph to scan; KD-73 stays open")
        return 0
    coordinates: dict[tuple[str, str, str], set[str]] = {}
    for lockfile in lockfiles:
        module = lockfile.parent.relative_to(ROOT).as_posix() if lockfile.parent != ROOT else "."
        for coordinate in parse_lock_text(lockfile.read_text(encoding="utf-8")):
            coordinates.setdefault(coordinate, set()).add(module)
    print(
        f"graph: {len(coordinates)} coordinates from {len(lockfiles)} lockfiles "
        f"({', '.join(sorted(p.name for p in lockfiles))})"
    )
    print(
        f"NOTE: lockfiles are untracked working-tree artifacts; the graph follows them, not HEAD "
        f"(runtime stdlib pin = {runtime_stdlib}, upstream requirement = {upstream_stdlib})"
    )

    android_jar = locate_android_jar(args.android_jar)
    runtime_jars = find_cached_stdlib_jar(runtime_stdlib)
    upstream_jars = find_cached_stdlib_jar(upstream_stdlib)
    if not runtime_jars:
        print(
            f"FAIL(environment): runtime kotlin-stdlib {runtime_stdlib} jar not in Gradle cache",
            file=sys.stderr,
        )
        return 2
    print(f"android.jar: {android_jar}")
    print(f"runtime stdlib {runtime_stdlib}: {runtime_jars[0]}")
    if upstream_jars:
        print(f"upstream stdlib {upstream_stdlib} (comparison only): {upstream_jars[0]}")
    else:
        print(
            f"NOTE: upstream stdlib {upstream_stdlib} jar not cached — fail-class/2.4-only 分级不可用，"
            "仅保留 unresolved 判定"
        )

    primary, _primary_refs, _p = index_and_refs_from_artifact(runtime_jars[0])
    android, _a_refs, a_problems = index_and_refs_from_artifact(
        android_jar, prefixes=JDK_PREFIXES
    )
    stdlib24: dict[str, ClassInfo] | None = None
    if upstream_jars:
        stdlib24, _u_refs, _u = index_and_refs_from_artifact(upstream_jars[0])
    print(
        f"indexes: primary={len(primary)} kotlin classes, android={len(android)} jdk classes, "
        f"stdlib24={len(stdlib24) if stdlib24 is not None else 'n/a'}"
    )

    runtime_coordinate = (STDLIB_GROUP, STDLIB_ARTIFACT, runtime_stdlib)
    secondary: dict[str, ClassInfo] = {}
    all_refs: dict[Ref, set[str]] = {}
    unscannable = 0
    missing: list[tuple[str, str, str]] = []
    scanned_artifacts = 0
    for coordinate in sorted(coordinates):
        files = find_artifact_files(coordinate)
        if not files:
            missing.append(coordinate)
            continue
        scanned_artifacts += 1
        if coordinate == runtime_coordinate:
            continue  # 主索引本体：不再扫自己的引用（对自身恒可解析）
        attribution = f"{coordinate[0]}:{coordinate[1]}:{coordinate[2]}"
        for path in files:
            index, refs, problems = index_and_refs_from_artifact(path)
            for name, info in index.items():
                secondary.setdefault(name, info)
            unscannable += len(problems)
            for problem in problems:
                print(f"  WARN unscannable: {problem}")
            for ref in refs:
                all_refs.setdefault(ref, set()).add(attribution)
    print(
        f"artifacts: {scanned_artifacts}/{len(coordinates)} coordinates scanned, "
        f"{len(missing)} without cached artifact (not scanned)"
    )
    if missing:
        for coordinate in missing:
            print(f"  MISSING: {':'.join(coordinate)}")
    verdicts = [
        resolve_ref(ref, primary=primary, android=android, secondary=secondary, stdlib24=stdlib24)
        for ref in sorted(all_refs, key=lambda r: r.as_text())
    ]
    buckets: dict[str, int] = {}
    for verdict in verdicts:
        buckets[verdict.status] = buckets.get(verdict.status, 0) + 1
    secondary_by_jar: dict[str, int] = {}
    for verdict in verdicts:
        if verdict.status == "resolved-secondary":
            secondary_by_jar[verdict.where or "?"] = secondary_by_jar.get(verdict.where or "?", 0) + 1
    nonstdlib_by_top: dict[str, int] = {}
    for verdict in verdicts:
        if verdict.status == "non-stdlib":
            top = "/".join(verdict.ref.owner.split("/")[:3])
            nonstdlib_by_top[top] = nonstdlib_by_top.get(top, 0) + 1

    print(
        f"unique kotlin/* refs: {len(verdicts)} from {scanned_artifacts} artifacts; "
        f"buckets: { {k: v for k, v in sorted(buckets.items())} }"
    )
    if secondary_by_jar:
        print(f"resolved-secondary owners: {len(secondary_by_jar)} (graph-internal jars, informational)")
    if nonstdlib_by_top:
        print(f"non-stdlib kotlin owners (informational): {nonstdlib_by_top}")
    if unscannable:
        print(f"unscannable class entries: {unscannable} (listed above; scan coverage is not total)")

    metadata_requires: dict[str, set[str]] = {}
    for coordinate in sorted(coordinates):
        for meta in find_metadata_files(coordinate):
            for version in declared_stdlib_requirements(meta):
                if version != runtime_stdlib:
                    metadata_requires.setdefault(f"{coordinate[0]}:{coordinate[1]}:{coordinate[2]}", set()).add(version)
    if metadata_requires:
        print(f"metadata leg: coordinates declaring kotlin-stdlib != {runtime_stdlib}: {metadata_requires}")
    else:
        print(f"metadata leg: no coordinate declares kotlin-stdlib != {runtime_stdlib}")

    errors = evaluate_verdicts(verdicts)
    if errors:
        print(f"FAIL: {len(errors)} unresolved stdlib reference(s):", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(
        f"OK: 0 unresolved stdlib member references across {len(verdicts)} unique kotlin/* refs "
        f"({scanned_artifacts} artifacts; {len(missing)} coordinates unscanned)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
