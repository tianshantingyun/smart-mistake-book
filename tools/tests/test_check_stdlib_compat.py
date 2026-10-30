# -*- coding: utf-8 -*-
"""stdlib 兼容证据门（tools/ci/check_stdlib_compat.py）的自测（KD-73）。

消灭的具体失败：批次 30 T7 自述过一次"10.3 万 class / 138,225 条引用在
2.3.21 全部可解析"的全解析图扫描，但扫描器、逐构件清单与结果文件均未落仓，
不可复跑 → UNVERIFIED（见 docs/known-defects.md KD-73 批次 31/33/34/35 各段）。
本测试把该证据钉成可复跑的纯函数语义：class 常量池解析、成员索引、
引用解析分桶、判定与锁图坐标解析，全部用合成 .class/jar 夹具覆盖，
不依赖真实 Gradle 缓存。

每条断言都配一个正例或反例，避免脚本静默通过。
"""

from __future__ import annotations

import io
import struct
import unittest
import zipfile
from pathlib import Path

from ci import check_stdlib_compat as compat


# ---------------------------------------------------------------- fixtures


class _CP:
    """最小常量池构造器：索引 1 起步，Long/Double 占两个槽位。"""

    def __init__(self) -> None:
        self.entries: list[tuple | None] = [None]

    def utf8(self, s: str) -> int:
        self.entries.append((1, s.encode("utf-8")))
        return len(self.entries) - 1

    def _wrap(self, tag: int, payload: object) -> int:
        self.entries.append((tag, payload))
        return len(self.entries) - 1

    def klass(self, name: str) -> int:
        return self._wrap(7, self.utf8(name))

    def name_and_type(self, name: str, desc: str) -> int:
        return self._wrap(12, (self.utf8(name), self.utf8(desc)))

    def member_ref(self, tag: int, owner: str, name: str, desc: str) -> int:
        return self._wrap(tag, (self.klass(owner), self.name_and_type(name, desc)))

    def constant(self, tag: int, value: int) -> int:
        """tag 3/4 = u4；tag 5/6 = u8 且额外吞一个槽位。"""
        self.entries.append((tag, value))
        index = len(self.entries) - 1
        if tag in (5, 6):
            self.entries.append(None)  # 占位槽：后续索引必须跳过它
        return index

    def serialize(self) -> bytes:
        out = bytearray()
        out += struct.pack(">H", len(self.entries))
        for entry in self.entries[1:]:
            if entry is None:
                continue  # Long/Double 的占位槽没有字节
            tag = entry[0]
            out += struct.pack(">B", tag)
            if tag == 1:
                body = entry[1]
                out += struct.pack(">H", len(body)) + body
            elif tag in (3, 4):
                out += struct.pack(">I", entry[1])
            elif tag in (5, 6):
                out += struct.pack(">Q", entry[1])
            elif tag in (7, 8, 16, 19, 20):
                out += struct.pack(">H", entry[1])
            elif tag in (9, 10, 11, 12, 17, 18):
                a, b = entry[1]
                out += struct.pack(">HH", a, b)
            elif tag == 15:
                out += struct.pack(">BH", entry[1][0], entry[1][1])
        return bytes(out)


def make_class_bytes(
    this_class: str,
    super_class: str | None = "java/lang/Object",
    interfaces: tuple[str, ...] = (),
    methods: tuple[tuple[str, str], ...] = (),
    fields: tuple[tuple[str, str], ...] = (),
    refs: tuple[tuple[int, str, str, str], ...] = (),
) -> bytes:
    """构造一个最小但结构合法的 .class；refs 直接进常量池。"""
    cp = _CP()
    this_index = cp.klass(this_class)
    super_index = cp.klass(super_class) if super_class else 0
    interface_indices = [cp.klass(name) for name in interfaces]

    def member_blob(name: str, desc: str) -> bytes:
        return struct.pack(
            ">HHHH", 0x0001, cp.utf8(name), cp.utf8(desc), 0
        )

    field_blobs = [member_blob(name, desc) for name, desc in fields]
    method_blobs = [member_blob(name, desc) for name, desc in methods]
    ref_blobs: list[int] = []
    for tag, owner, name, desc in refs:
        ref_blobs.append(cp.member_ref(tag, owner, name, desc))
    # 一个 Long 常量，验证解析器跳过占位槽后仍能对齐后续引用。
    cp.constant(5, 0x123456789ABCDEF0)

    out = bytearray()
    out += struct.pack(">IHH", 0xCAFEBABE, 0, 52)
    out += cp.serialize()
    out += struct.pack(">HHH", 0x0021, this_index, super_index)
    out += struct.pack(">H", len(interface_indices))
    for index in interface_indices:
        out += struct.pack(">H", index)
    out += struct.pack(">H", len(field_blobs)) + b"".join(field_blobs)
    out += struct.pack(">H", len(method_blobs)) + b"".join(method_blobs)
    out += struct.pack(">H", 0)
    return bytes(out)


def make_jar_bytes(entries: dict[str, bytes]) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return buf.getvalue()


def make_aar_bytes(class_entries: dict[str, bytes]) -> bytes:
    return make_jar_bytes({"classes.jar": make_jar_bytes(class_entries)})


METHOD_REF = 10
FIELD_REF = 9
IFACE_REF = 11

# 合成"stdlib 2.3.21"：facade 继承链 + 一个只在本版本存在的类。
STDLIB_23: dict[str, bytes] = {
    "kotlin/collections/CollectionsKt.class": make_class_bytes(
        "kotlin/collections/CollectionsKt",
        super_class="kotlin/collections/CollectionsKt___CollectionsKt",
    ),
    "kotlin/collections/CollectionsKt___CollectionsKt.class": make_class_bytes(
        "kotlin/collections/CollectionsKt___CollectionsKt",
        methods=(("toList", "(Ljava/lang/Iterable;)Ljava/util/List;"),),
    ),
    "kotlin/old/OldKt.class": make_class_bytes(
        "kotlin/old/OldKt",
        methods=(("since23", "()V"),),
        fields=(("flag", "Z"),),
    ),
    "kotlin/text/FakeBuilder.class": make_class_bytes(
        "kotlin/text/FakeBuilder",
        super_class="java/lang/StringBuilder",
    ),
    "kotlin/jvm/functions/Function1.class": make_class_bytes(
        "kotlin/jvm/functions/Function1",
        super_class=None,
        interfaces=("kotlin/Function",),
        methods=(("invoke", "(Ljava/lang/Object;)Ljava/lang/Object;"),),
    ),
}

# 合成"stdlib 2.4.10"：多一个新类 + 一个只在本版本的成员。
STDLIB_24: dict[str, bytes] = dict(STDLIB_23)
STDLIB_24["kotlin/newpack/NewKt.class"] = make_class_bytes(
    "kotlin/newpack/NewKt",
    methods=(("newThing", "()V"),),
)
STDLIB_24["kotlin/old/OldKt.class"] = make_class_bytes(
    "kotlin/old/OldKt",
    methods=(("since23", "()V"), ("only24", "()V")),
    fields=(("flag", "Z"),),
)

# 合成 android.jar（只需覆盖走出 stdlib 的 java/* 链）。
ANDROID_JAR: dict[str, bytes] = {
    "java/lang/StringBuilder.class": make_class_bytes(
        "java/lang/StringBuilder",
        methods=(("length", "()I"), ("toString", "()Ljava/lang/String;")),
    ),
    "java/lang/Object.class": make_class_bytes("java/lang/Object"),
}

# 合成"图中其它 jar"：kotlin-reflect 类型的声明者。
SECONDARY_JAR: dict[str, bytes] = {
    "kotlin/reflect/full/KDeclarations.class": make_class_bytes(
        "kotlin/reflect/full/KDeclarations",
        methods=(("dummy", "()V"),),
    ),
}


def build_indexes() -> tuple[dict, dict, dict, dict]:
    primary = compat.build_class_index(make_jar_bytes(STDLIB_23))
    android = compat.build_class_index(make_jar_bytes(ANDROID_JAR), prefixes=compat.JDK_PREFIXES)
    secondary = compat.build_class_index(make_jar_bytes(SECONDARY_JAR))
    stdlib24 = compat.build_class_index(make_jar_bytes(STDLIB_24))
    return primary, android, secondary, stdlib24


def resolve(owner: str, name: str, desc: str):
    primary, android, secondary, stdlib24 = build_indexes()
    return compat.resolve_ref(
        compat.Ref(owner, name, desc),
        primary=primary,
        android=android,
        secondary=secondary,
        stdlib24=stdlib24,
    )


# ---------------------------------------------------------------- tests


class ParseClassTest(unittest.TestCase):
    def test_extracts_only_kotlin_member_refs(self) -> None:
        data = make_class_bytes(
            "com/example/Use",
            refs=(
                (METHOD_REF, "kotlin/collections/CollectionsKt", "toList", "(Ljava/lang/Iterable;)Ljava/util/List;"),
                (FIELD_REF, "kotlin/old/OldKt", "flag", "Z"),
                (IFACE_REF, "kotlin/jvm/functions/Function1", "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                (METHOD_REF, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
            ),
        )
        info, refs = compat.parse_class(data)
        self.assertEqual(info.name, "com/example/Use")
        owners = {(ref.owner, ref.name, ref.desc) for ref in refs}
        self.assertIn(("kotlin/collections/CollectionsKt", "toList", "(Ljava/lang/Iterable;)Ljava/util/List;"), owners)
        self.assertIn(("kotlin/old/OldKt", "flag", "Z"), owners)
        self.assertIn(("kotlin/jvm/functions/Function1", "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;"), owners)
        # java/* 引用不是 stdlib 兼容问题的对象，不提取。
        self.assertNotIn(("java/lang/StringBuilder", "toString", "()Ljava/lang/String;"), owners)
        self.assertEqual(len(refs), 3)

    def test_class_info_captures_hierarchy_and_members(self) -> None:
        data = make_class_bytes(
            "kotlin/old/OldKt",
            super_class="kotlin/Base",
            interfaces=("kotlin/Marker",),
            methods=(("since23", "()V"),),
            fields=(("flag", "Z"),),
        )
        info, _ = compat.parse_class(data)
        assert info is not None
        self.assertEqual(info.name, "kotlin/old/OldKt")
        self.assertEqual(info.super_name, "kotlin/Base")
        self.assertEqual(info.interfaces, ("kotlin/Marker",))
        self.assertIn(("since23", "()V"), info.members)
        self.assertIn(("flag", "Z"), info.members)

    def test_truncated_class_raises(self) -> None:
        with self.assertRaises(ValueError):
            compat.parse_class(b"\xca\xfe\xba\xbe\x00\x00")


class BuildIndexTest(unittest.TestCase):
    def test_jar_index_uses_declared_hierarchy(self) -> None:
        index = compat.build_class_index(make_jar_bytes(STDLIB_23))
        self.assertIn("kotlin/collections/CollectionsKt", index)
        facade = index["kotlin/collections/CollectionsKt"]
        self.assertEqual(
            facade.super_name, "kotlin/collections/CollectionsKt___CollectionsKt"
        )
        holder = index["kotlin/collections/CollectionsKt___CollectionsKt"]
        self.assertIn(("toList", "(Ljava/lang/Iterable;)Ljava/util/List;"), holder.members)

    def test_nested_aar_classes_jar_is_indexed(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            aar = Path(tmp) / "lib.aar"
            aar.write_bytes(make_aar_bytes(SECONDARY_JAR))
            index, refs, problems = compat.index_and_refs_from_artifact(aar)
            self.assertEqual(problems, [])
            self.assertIn("kotlin/reflect/full/KDeclarations", index)
            self.assertTrue(
                any(ref.owner == "kotlin/reflect/full/KDeclarations" for ref in refs) or refs == []
            )


class LockGraphTest(unittest.TestCase):
    def test_collect_coordinates_from_lockfiles(self) -> None:
        text = "\n".join(
            [
                "# comment line",
                "",
                "org.jetbrains.kotlin:kotlin-stdlib:2.3.21=compileClasspath",
                "androidx.annotation:annotation:1.9.1=debugCompileClasspath,debugRuntimeClasspath",
            ]
        )
        coords = compat.parse_lock_text(text)
        self.assertEqual(
            coords,
            {
                ("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
                ("androidx.annotation", "annotation", "1.9.1"),
            },
        )


class ResolveRefTest(unittest.TestCase):
    def test_member_declared_on_facade_super_resolves(self) -> None:
        verdict = resolve(
            "kotlin/collections/CollectionsKt",
            "toList",
            "(Ljava/lang/Iterable;)Ljava/util/List;",
        )
        self.assertEqual(verdict.status, "resolved")
        self.assertEqual(verdict.where, "kotlin/collections/CollectionsKt___CollectionsKt")

    def test_interface_member_resolves(self) -> None:
        verdict = resolve(
            "kotlin/jvm/functions/Function1",
            "invoke",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
        )
        self.assertEqual(verdict.status, "resolved")

    def test_walk_out_to_android_jar_resolves(self) -> None:
        verdict = resolve("kotlin/text/FakeBuilder", "length", "()I")
        self.assertEqual(verdict.status, "resolved")
        self.assertEqual(verdict.where, "java/lang/StringBuilder")

    def test_class_only_in_24_fails_as_class_loss(self) -> None:
        verdict = resolve("kotlin/newpack/NewKt", "newThing", "()V")
        self.assertEqual(verdict.status, "fail-class")

    def test_member_only_in_24_fails_as_member_loss(self) -> None:
        verdict = resolve("kotlin/old/OldKt", "only24", "()V")
        self.assertEqual(verdict.status, "fail-member")
        self.assertTrue(verdict.note and "2.4.10" in verdict.note)

    def test_member_in_neither_version_fails(self) -> None:
        verdict = resolve("kotlin/old/OldKt", "ghost", "()V")
        self.assertEqual(verdict.status, "fail-member")

    def test_owner_declared_by_secondary_graph_jar_is_informational(self) -> None:
        verdict = resolve("kotlin/reflect/full/KDeclarations", "dummy", "()V")
        self.assertEqual(verdict.status, "resolved-secondary")

    def test_owner_nowhere_is_informational(self) -> None:
        verdict = resolve("kotlin/nowhere/Ghost", "x", "()V")
        self.assertEqual(verdict.status, "non-stdlib")

    def test_secondary_member_does_not_mask_stdlib_loss(self) -> None:
        # 图内其它构件声明同名成员（遮蔽）也不能把"2.4-only 成员"洗成 resolved ——
        # 运行时 stdlib 真缺这个成员，降级失败模式成立。
        primary, android, _secondary, stdlib24 = build_indexes()
        shaded = compat.build_class_index(
            make_jar_bytes(
                {
                    "kotlin/old/OldKt.class": make_class_bytes(
                        "kotlin/old/OldKt",
                        methods=(("only24", "()V"),),
                    )
                }
            )
        )
        verdict = compat.resolve_ref(
            compat.Ref("kotlin/old/OldKt", "only24", "()V"),
            primary=primary,
            android=android,
            secondary=shaded,
            stdlib24=stdlib24,
        )
        self.assertEqual(verdict.status, "fail-member")

    def test_secondary_only_member_is_informational(self) -> None:
        # 成员在任何 stdlib 版本里都不存在、只由图内其它 jar 声明 —— 不属
        # stdlib 降级问题，归入 resolved-secondary 信息桶（带归因，不是绿）。
        primary, android, secondary, stdlib24 = build_indexes()
        shaded = compat.build_class_index(
            make_jar_bytes(
                {
                    "kotlin/old/OldKt.class": make_class_bytes(
                        "kotlin/old/OldKt",
                        methods=(("shaded", "()V"),),
                    )
                }
            )
        )
        verdict = compat.resolve_ref(
            compat.Ref("kotlin/old/OldKt", "shaded", "()V"),
            primary=primary,
            android=android,
            secondary=shaded,
            stdlib24=stdlib24,
        )
        self.assertEqual(verdict.status, "resolved-secondary")


class EvaluateTest(unittest.TestCase):
    def test_no_failures_passes(self) -> None:
        verdicts = [
            compat.RefVerdict(compat.Ref("kotlin/old/OldKt", "since23", "()V"), "resolved", "kotlin/old/OldKt", None),
            compat.RefVerdict(compat.Ref("kotlin/nowhere/Ghost", "x", "()V"), "non-stdlib", None, None),
        ]
        self.assertEqual(compat.evaluate_verdicts(verdicts), [])

    def test_fail_class_and_fail_member_are_reported(self) -> None:
        verdicts = [
            compat.RefVerdict(compat.Ref("kotlin/newpack/NewKt", "newThing", "()V"), "fail-class", None, None),
            compat.RefVerdict(compat.Ref("kotlin/old/OldKt", "only24", "()V"), "fail-member", None, "2.4.10-only"),
        ]
        errors = compat.evaluate_verdicts(verdicts)
        self.assertEqual(len(errors), 2)
        self.assertTrue(any("fail-class" in e and "NewKt" in e for e in errors))
        self.assertTrue(any("fail-member" in e and "only24" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
