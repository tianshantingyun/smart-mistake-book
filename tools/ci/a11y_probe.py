#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""I 域设备探针：语义树导出 + 状态切换 + 结构审计 + 设备绑定证据。

## 它消灭的具体失败

`a11y_audit.py` 只能审计一个 dump 文件，但“这个 dump 是哪台设备、哪个 APK、
哪种字号/主题/方向下截的”在批次 43 之前没有记录。于是同一张图可以被标成
“字号 200% 正常”“暗色正常”“横屏正常”三种结论。本探针把状态切换、dump、
审计与绑定写进**同一次设备轮次**：

- 每次采集绑定 `serial` + `boot_id` + `apk_sha256` + 密度 + 状态参数；
- 状态在采集后恢复（字号 1.0、夜间 no、旋转 0），不把设备留在异常态；
- 审计结果直接引用 `a11y_audit.audit()`，违规即该次采集 `verdict=FAIL`；
- 键盘/焦点走查记录逐次焦点节点，焦点节点无朗读文本即失败。

边界：本工具不启动 TalkBack 服务（需要人工在设置里开启无障碍服务），
不判定朗读语感；这些保持 UNVERIFIED/BLOCKED 并指向 runbook。

用法：

    python -I tools/ci/a11y_probe.py capture --serial emulator-5562 \
      --label base-library --root-tag root_library \
      --nav nav_library --out-dir docs/research/evidence/2026-11-05-batch-44/i-domain

    python -I tools/ci/a11y_probe.py capture ... --font-scale 2.0
    python -I tools/ci/a11y_probe.py capture ... --night yes --rotation 1
    python -I tools/ci/a11y_probe.py focus --serial ... --label focus-library \
      --root-tag root_library --presses 6 --out-dir ...
    python -I tools/ci/a11y_probe.py keyboard --serial ... --label keyboard-search \
      --field <resource-id> --text 三角形 --out-dir ...

退出码：0 该次采集全部断言成立；1 有失败；2 参数/设备不可用。
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import a11y_audit as A  # noqa: E402

PACKAGE = "com.tingyun.smartmistakebook.localfirst"
MAIN_ACTIVITY = "com.tingyun.smartmistakebook.MainActivity"
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def find_adb(explicit: str | None) -> str:
    if explicit:
        return explicit
    found = shutil.which("adb")
    if found:
        return found
    for candidate in (
        r"C:\Android\Sdk\platform-tools\adb.exe",
        "/c/Android/Sdk/platform-tools/adb.exe",
    ):
        if Path(candidate).exists():
            return candidate
    return "adb"


class Device:
    def __init__(self, adb: str, serial: str, package: str = PACKAGE,
                 timeout: int = 90) -> None:
        self.adb = adb
        self.serial = serial
        self.package = package
        self.timeout = timeout
        self.checks: list[str] = []
        self.failures: list[str] = []

    def shell(self, *args: str, timeout: int | None = None) -> str:
        completed = subprocess.run(
            [self.adb, "-s", self.serial, "shell", *args],
            capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=timeout or self.timeout,
        )
        return (completed.stdout or "") + (completed.stderr or "")

    def exec_out_bytes(self, *args: str, timeout: int | None = None) -> bytes:
        completed = subprocess.run(
            [self.adb, "-s", self.serial, "exec-out", *args],
            capture_output=True, timeout=timeout or self.timeout,
        )
        return completed.stdout or b""

    def boot_id(self) -> str:
        return self.shell("cat", "/proc/sys/kernel/random/boot_id").strip()

    def density(self) -> int:
        text = self.shell("wm", "density")
        values = [int(v) for v in re.findall(r"(?:Physical|Override) density:\s*(\d+)", text)]
        return values[-1] if values else 160

    def apk_sha256(self) -> str:
        paths = [line.split(":", 1)[1] for line in
                 self.shell("pm", "path", self.package).splitlines()
                 if line.startswith("package:")]
        if not paths:
            raise RuntimeError(f"{self.package} 未安装")
        remote = paths[0]
        digest = hashlib.sha256()
        digest.update(self.exec_out_bytes("cat", remote, timeout=180))
        return digest.hexdigest()

    def dump(self) -> str:
        self.shell("uiautomator", "dump", "/sdcard/i_domain_dump.xml")
        return self.exec_out_bytes("cat", "/sdcard/i_domain_dump.xml").decode(
            "utf-8", errors="replace")

    def launch(self) -> None:
        self.shell("am", "start", "-n", f"{self.package}/{MAIN_ACTIVITY}")
        time.sleep(4)

    def tap(self, x: int, y: int) -> None:
        self.shell("input", "tap", str(x), str(y))

    def wait_tag(self, tag: str, timeout: int = 25) -> bool:
        deadline = time.time() + timeout
        while time.time() < deadline:
            xml = self.dump()
            if f'testTag/{tag}' in xml or tag in xml:
                return True
            time.sleep(0.8)
        return False


def _nodes(xml_text: str) -> list[dict]:
    try:
        return A.parse_dump(xml_text)
    except Exception:  # noqa: BLE001 - dump 可能被系统页面截断
        return []


def _label_of(node: dict) -> str:
    return (node.get("content_desc") or node.get("text") or "").strip()


def center(bounds: str) -> tuple[int, int] | None:
    match = BOUNDS_RE.search(bounds or "")
    if not match:
        return None
    x1, y1, x2, y2 = (int(v) for v in match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8")


def _binding(device: Device) -> dict:
    return {
        "serial": device.serial,
        "boot_id": device.boot_id(),
        "apk_sha256": device.apk_sha256(),
        "package": device.package,
        "density_dpi": device.density(),
        "sdk": device.shell("getprop", "ro.build.version.sdk").strip(),
    }


def _set_state(device: Device, args: argparse.Namespace) -> dict:
    state: dict = {}
    if args.font_scale is not None:
        device.shell("settings", "put", "system", "font_scale", str(args.font_scale))
        state["font_scale"] = args.font_scale
    if args.night is not None:
        device.shell("cmd", "uimode", "night", args.night)
        state["night"] = args.night
    if args.rotation is not None:
        device.shell("settings", "put", "system", "accelerometer_rotation", "0")
        device.shell("settings", "put", "system", "user_rotation", str(args.rotation))
        state["rotation"] = args.rotation
    if args.wm_size:
        # 传感器旋转在无窗口模拟器上不生效；用显示尺寸覆盖得到横屏窗口尺寸，
        # 采集后 `wm size reset` 还原。证据里必须写明这一点。
        device.shell("wm", "size", args.wm_size)
        state["wm_size"] = args.wm_size
    if state:
        time.sleep(2.0)
    return state


def _restore_state(device: Device, state: dict) -> None:
    if "font_scale" in state:
        device.shell("settings", "put", "system", "font_scale", "1.0")
    if "night" in state:
        device.shell("cmd", "uimode", "night", "no")
    if "rotation" in state:
        device.shell("settings", "put", "system", "user_rotation", "0")
    if "wm_size" in state:
        device.shell("wm", "size", "reset")
    if state:
        device.shell("am", "force-stop", device.package)
        time.sleep(0.5)


def _navigate(device: Device, nav: list[str]) -> None:
    for tag in nav:
        if not tag:
            continue
        xml = device.dump()
        node = next((n for n in _nodes(xml) if tag in (n.get("resource_id") or "")), None)
        if node is None:
            device.failures.append(f"导航节点缺失: {tag}")
            return
        point = center(node.get("bounds", ""))
        if point is None:
            device.failures.append(f"导航节点无坐标: {tag}")
            return
        device.tap(*point)
        time.sleep(1.5)
        device.checks.append(f"tapped {tag}")


def _audit_payload(xml_text: str, density: int, colors: dict | None) -> dict:
    nodes = A.parse_dump(xml_text)
    return A.audit(nodes, density_dpi=density, colors=colors)


def _finish(label: str, payload: dict, out_dir: Path, device: Device,
            log_lines: list[str]) -> int:
    payload["failures"] = device.failures
    payload["checks"] = device.checks
    verdict = "PASS" if not device.failures else "FAIL"
    payload["verdict"] = verdict
    payload["finished_at"] = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    _write_json(out_dir / f"{label}.device.json", payload)
    code = 0 if verdict == "PASS" else 1
    lines = list(log_lines) + [f"VERDICT={verdict}"] + \
        [f"FAILURE {item}" for item in device.failures] + [f"EXIT={code}"]
    (out_dir / f"{label}.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return code


def _cmd_capture(args: argparse.Namespace, device: Device) -> int:
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    binding = _binding(device)
    colors = A.load_colors(args.colors)
    state = _set_state(device, args)
    try:
        device.shell("am", "force-stop", device.package)
        time.sleep(1)
        device.launch()
        if args.nav:
            _navigate(device, [t for t in args.nav.split(",") if t])
        if args.root_tag and not device.wait_tag(args.root_tag):
            device.failures.append(f"根页面标签缺失: {args.root_tag}")
        else:
            if args.root_tag:
                device.checks.append(f"root tag present: {args.root_tag}")
        xml = device.dump()
        (out_dir / f"{args.label}.xml").write_text(xml, encoding="utf-8")
        rotation = re.search(r'rotation="(\d)"', xml[:200])
        if args.rotation is not None:
            actual = int(rotation.group(1)) if rotation else -1
            if actual != args.rotation:
                device.failures.append(
                    f"旋转未生效: 请求 {args.rotation} 实际 {actual}")
            else:
                device.checks.append(f"rotation={actual}")
        if args.wm_size:
            first_node = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
            if first_node:
                width = int(first_node.group(3)) - int(first_node.group(1))
                height = int(first_node.group(4)) - int(first_node.group(2))
                if width <= height:
                    device.failures.append(
                        f"wm size 未产生横屏窗口: {width}x{height}")
                else:
                    device.checks.append(f"landscape window {width}x{height}")
            else:
                device.failures.append("dump 无 bounds，无法确认横屏窗口")
        result = _audit_payload(xml, binding["density_dpi"], colors)
        _write_json(out_dir / f"{args.label}.audit.json", result)
        if not result["structural_ok"]:
            device.failures.append(
                f"结构审计违规: {result['violation_codes']}")
        if colors is not None and result["contrast"]["status"] != "PASS":
            device.failures.append(f"对比度未通过: {result['contrast']['status']}")
        if args.expect_text:
            if args.expect_text not in xml:
                device.failures.append(f"期望文本缺失: {args.expect_text}")
            else:
                device.checks.append(f"text present: {args.expect_text}")
        if not xml.strip():
            device.failures.append("dump 为空")
        log = [
            f"label={args.label}",
            f"serial={binding['serial']} boot_id={binding['boot_id']}",
            f"apk_sha256={binding['apk_sha256']}",
            f"density_dpi={binding['density_dpi']} state={json.dumps(state, sort_keys=True)}",
            f"NODES={result['nodes']} CLICKABLE={result['clickable']}",
            f"STRUCTURAL={'PASS' if result['structural_ok'] else 'FAIL'}",
            f"CONTRAST={result['contrast']['status']}",
            f"VIOLATIONS={result['violation_codes']}",
        ]
        payload = {"label": args.label, "state": state, "action": "capture",
                   "started_at": _dt.datetime.now(_dt.timezone.utc).strftime(
                       "%Y-%m-%dT%H:%M:%SZ"), **binding,
                   "nodes": result["nodes"], "clickable": result["clickable"],
                   "structural_ok": result["structural_ok"],
                   "contrast": result["contrast"]["status"],
                   "violation_codes": result["violation_codes"]}
        return _finish(args.label, payload, out_dir, device, log)
    finally:
        _restore_state(device, state)


def _cmd_focus(args: argparse.Namespace, device: Device) -> int:
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    binding = _binding(device)
    state = _set_state(device, args)
    try:
        device.shell("am", "force-stop", device.package)
        time.sleep(1)
        device.launch()
        if args.nav:
            _navigate(device, [t for t in args.nav.split(",") if t])
        if args.root_tag and not device.wait_tag(args.root_tag):
            device.failures.append(f"根页面标签缺失: {args.root_tag}")
        sequence: list[dict] = []
        seen: set[str] = set()
        for press in range(1, args.presses + 1):
            device.shell("input", "keyevent", "KEYCODE_TAB")
            time.sleep(0.6)
            xml = device.dump()
            focused = [n for n in _nodes(xml) if n.get("focused")]
            if not focused:
                sequence.append({"press": press, "focused": None})
                device.checks.append(f"press {press}: 无焦点节点")
                continue
            node = focused[-1]
            # Compose 的可聚焦容器常把文本放在后代节点（合并语义），只用节点自身
            # 的 text/desc 会把有朗读文本的控件误判成无标签。
            label = A._merged_label(_nodes(xml), node["index"]) or _label_of(node)
            sequence.append({
                "press": press,
                "label": label,
                "class": node.get("class"),
                "bounds": node.get("bounds"),
                "focused": True,
            })
            if not label:
                device.failures.append(f"第 {press} 次 TAB 焦点节点无朗读文本 "
                                       f"{node.get('class')} {node.get('bounds')}")
            seen.add(label)
        if args.presses and len([s for s in sequence if s.get("focused")]):
            pass
        (out_dir / f"{args.label}.focus.json").write_text(
            json.dumps(sequence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        # 焦点顺序必须覆盖至少 2 个不同节点，否则“顺序”没有意义。
        if len(seen) < 2:
            device.failures.append(f"TAB 焦点只覆盖 {len(seen)} 个不同节点")
        else:
            device.checks.append(f"focus labels: {sorted(seen)}")
        payload = {"label": args.label, "action": "focus", **binding,
                   "presses": args.presses, "distinct": sorted(seen),
                   "sequence": sequence,
                   "started_at": _dt.datetime.now(_dt.timezone.utc).strftime(
                       "%Y-%m-%dT%H:%M:%SZ")}
        return _finish(args.label, payload, out_dir, device,
                       [f"label={args.label} presses={args.presses} "
                        f"distinct={sorted(seen)}"])
    finally:
        _restore_state(device, state)


def _cmd_keyboard(args: argparse.Namespace, device: Device) -> int:
    if not args.text.isascii():
        print("ERROR: adb `input text` 只支持 ASCII；中文输入走 instrumented 用例")
        print("EXIT=2")
        return 2
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    binding = _binding(device)
    state = _set_state(device, args)
    try:
        device.shell("am", "force-stop", device.package)
        time.sleep(1)
        device.launch()
        if args.nav:
            _navigate(device, [t for t in args.nav.split(",") if t])
        xml = device.dump()
        field = next((n for n in _nodes(xml)
                      if args.field in (n.get("resource_id") or "")), None)
        if field is None:
            device.failures.append(f"输入框缺失: {args.field}")
            payload = {"label": args.label, "action": "keyboard", **binding}
            return _finish(args.label, payload, out_dir, device, ["field missing"])
        point = center(field.get("bounds", ""))
        if point is None:
            device.failures.append("输入框无坐标")
            payload = {"label": args.label, "action": "keyboard", **binding}
            return _finish(args.label, payload, out_dir, device, ["no bounds"])
        device.tap(*point)
        time.sleep(1.0)
        ime = device.shell("dumpsys", "input_method")
        ime_shown = "mInputShown=true" in ime or "mIsInputViewShown=true" in ime
        if ime_shown:
            device.checks.append("IME shown after focus")
        else:
            device.failures.append("点击输入框后输入法未显示")
        # `adb shell input text` 只送 ASCII；中文走 instrumented 用例（I 域台账注明）。
        device.shell("input", "text", args.text.replace(" ", "%s"))
        time.sleep(1.0)
        device.shell("input", "keyevent", "KEYCODE_ENTER")
        time.sleep(1.5)
        after = device.dump()
        (out_dir / f"{args.label}.xml").write_text(after, encoding="utf-8")
        if args.text not in after:
            device.failures.append("输入文本未出现在语义树")
        else:
            device.checks.append(f"text entered: {args.text}")
        if args.expect_tag and args.expect_tag not in after:
            device.failures.append(f"回车后未见标签: {args.expect_tag}")
        if "AndroidRuntime: FATAL" in device.shell("logcat", "-d", "-t", "200"):
            device.failures.append("logcat 出现 FATAL")
        payload = {"label": args.label, "action": "keyboard", **binding,
                   "field": args.field, "text": args.text,
                   "started_at": _dt.datetime.now(_dt.timezone.utc).strftime(
                       "%Y-%m-%dT%H:%M:%SZ")}
        return _finish(args.label, payload, out_dir, device,
                       [f"label={args.label} field={args.field} text={args.text}"])
    finally:
        _restore_state(device, state)


def _cmd_talkback(args: argparse.Namespace, device: Device) -> int:
    """启用 TalkBack，走查手势并记录服务绑定 / 真实发声 / 语义树仍在。

    边界：不记录朗读文本内容（需要外部硬件驱动回传），因此本命令只认证
    “服务已绑定 + 触摸探索开启 + 手势触发真实 TTS 请求 + 语义树存在”。
    朗读内容是否准确、语感是否自然仍是 UNVERIFIED。
    """
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    binding = _binding(device)
    service = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
    before = device.shell("settings", "get", "secure", "enabled_accessibility_services").strip()
    try:
        device.shell("settings", "put", "secure", "enabled_accessibility_services", service)
        device.shell("settings", "put", "secure", "accessibility_enabled", "1")
        time.sleep(4)
        state = device.shell("dumpsys", "accessibility")
        (out_dir / f"{args.label}.accessibility.txt").write_text(state, encoding="utf-8")
        if "label=TalkBack" not in state:
            device.failures.append("TalkBack 服务未绑定")
        else:
            device.checks.append("TalkBack bound")
        if "touchExplorationEnabled=true" not in state:
            device.failures.append("触摸探索未开启")
        else:
            device.checks.append("touch exploration enabled")

        device.shell("logcat", "-c")
        device.shell("am", "force-stop", device.package)
        time.sleep(1)
        device.launch()
        if args.root_tag and not device.wait_tag(args.root_tag):
            device.failures.append(f"根页面标签缺失: {args.root_tag}")
        for _ in range(3):
            device.shell("input", "swipe", "540", "1800", "540", "600", "300")
            time.sleep(1.5)
        logcat = device.shell("logcat", "-d", "-t", "1500")
        (out_dir / f"{args.label}.logcat.txt").write_text(logcat, encoding="utf-8")
        speech = logcat.count("Synthesis request") + logcat.count("SpeechControllerImpl")
        if speech < 1:
            device.failures.append("未观察到 TalkBack TTS 请求（可能未发声）")
        else:
            device.checks.append(f"speech requests={speech}")
        xml = device.dump()
        (out_dir / f"{args.label}.xml").write_text(xml, encoding="utf-8")
        nodes = len(_nodes(xml))
        if nodes < 5:
            device.failures.append(f"语义树节点过少: {nodes}")
        else:
            device.checks.append(f"nodes={nodes}")
        if "AndroidRuntime: FATAL" in logcat:
            device.failures.append("logcat 出现 FATAL")
        payload = {"label": args.label, "action": "talkback", **binding,
                   "service": service, "speech_requests": speech, "nodes": nodes,
                   "started_at": _dt.datetime.now(_dt.timezone.utc).strftime(
                       "%Y-%m-%dT%H:%M:%SZ")}
        return _finish(args.label, payload, out_dir, device,
                       [f"label={args.label} talkback service={service} speech={speech}"])
    finally:
        if before:
            device.shell("settings", "put", "secure", "enabled_accessibility_services", before)
        else:
            device.shell("settings", "put", "secure", "enabled_accessibility_services", "")
        device.shell("settings", "put", "secure", "accessibility_enabled", "0")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="I 域设备探针")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--adb")
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--label", required=True)
        p.add_argument("--out-dir", required=True)
        p.add_argument("--colors")
        p.add_argument("--font-scale", type=float)
        p.add_argument("--night", choices=["yes", "no"])
        p.add_argument("--rotation", type=int, choices=[0, 1])
        p.add_argument("--wm-size", default="")
        p.add_argument("--nav", default="")
        p.add_argument("--root-tag", default="")

    talkback = sub.add_parser("talkback")
    common(talkback)
    talkback.set_defaults(func=_cmd_talkback)

    capture = sub.add_parser("capture")
    common(capture)
    capture.add_argument("--expect-text", default="")
    capture.set_defaults(func=_cmd_capture)

    focus = sub.add_parser("focus")
    common(focus)
    focus.add_argument("--presses", type=int, default=6)
    focus.set_defaults(func=_cmd_focus)

    keyboard = sub.add_parser("keyboard")
    common(keyboard)
    keyboard.add_argument("--field", required=True)
    keyboard.add_argument("--text", required=True)
    keyboard.add_argument("--expect-tag", default="")
    keyboard.set_defaults(func=_cmd_keyboard)
    return parser


def main(argv: list[str] | None = None) -> int:
    # 设备语义树含任意 Unicode；Windows 控制台默认 GBK 会在打印证据时崩掉。
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass
    args = build_parser().parse_args(argv)
    device = Device(find_adb(args.adb), args.serial, package=args.package)
    try:
        return int(args.func(args, device))
    except (subprocess.TimeoutExpired, RuntimeError) as exc:
        print(f"ERROR: {exc}")
        print("EXIT=2")
        return 2


if __name__ == "__main__":
    sys.exit(main())
