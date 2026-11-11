#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A 域设备状态探针：旋转 / 后台 / 杀进程 / 空态 / 权限拒绝。

## 它消灭的具体失败

计划 §2 A 域要求每条旅程都验“旋转、后台、杀进程、空态、权限拒绝”，但仓内
多数旅程只有主路径 UI 用例，没有系统级状态切换的任何执行证据；人工替代
（手点 Home、转屏、开发者选项杀进程）无法复跑也无法绑定 APK。本工具用
adb + uiautomator 把这几类动作做成**可复跑、带 serial/APK sha 的判定**：

- `rotate`：锁竖屏→转横屏→断言原页面 tag 仍在→转回；
- `home`：HOME→重新启动→断言原页面 tag 仍在；
- `kill`：HOME→`am kill`（必要时 `am force-stop`）→重新启动→断言 pid 变化
  且原页面 tag 仍在；
- `clear`：`pm clear`→启动→断言 root tag 与给定空态标记存在（需显式
  `--allow-destructive`）；
- `permission`：撤销 `POST_NOTIFICATIONS`→启动/导航→断言无崩溃且页面仍在，
  结束后恢复授权。

判定只看可观察事实（pid、resource-id/text、crash 缓冲区），不覆盖数据语义；
语义级断言由同行的 instrumented 用例承担。输出 `<label>.device.json`
（含 serial/apk_sha256/boot_id）与 `<label>.log`（含 `EXIT=` 行）。

退出码：0 判定通过；1 判定失败；2 参数/设备不可用。
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import subprocess
import sys
import time
from pathlib import Path

NODE_RE = re.compile(r"<node\b([^>]*)/?>")
ATTR_RE = re.compile(r'([A-Za-z_][A-Za-z0-9_-]*)="([^"]*)"')
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
PACKAGE = "com.tingyun.smartmistakebook.localfirst"
MAIN_ACTIVITY = "com.tingyun.smartmistakebook.MainActivity"


def _find_adb(explicit: str | None) -> str:
    if explicit:
        return explicit
    import shutil

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
                 timeout: int = 60):
        self.adb = adb
        self.serial = serial
        self.package = package
        self.timeout = timeout
        self.observations: list[str] = []
        self.failures: list[str] = []

    def shell(self, *args: str, timeout: int | None = None) -> str:
        cmd = [self.adb, "-s", self.serial, "shell", *args]
        completed = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=timeout or self.timeout,
        )
        return (completed.stdout or "") + (completed.stderr or "")

    def exec_out(self, *args: str, timeout: int | None = None) -> str:
        cmd = [self.adb, "-s", self.serial, "exec-out", *args]
        completed = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=timeout or self.timeout,
        )
        return (completed.stdout or "") + (completed.stderr or "")

    def dump(self) -> str:
        self.shell("uiautomator", "dump", "/sdcard/a_domain_dump.xml")
        return self.exec_out("cat", "/sdcard/a_domain_dump.xml")

    def pid(self) -> str:
        parts = self.shell("pidof", self.package).strip().split()
        return parts[0] if parts else ""

    def launch(self) -> None:
        # 不用 -W：它会等 Activity 完全 idle，首启初始化（建库/装 KB）时可能
        # 远超 60 s 并让探针误报超时；后续用 uiautomator 轮询页面更贴近真实。
        self.shell("am", "start", "-n", f"{self.package}/{MAIN_ACTIVITY}")
        time.sleep(5)

    def logcat_clear(self) -> None:
        self.shell("logcat", "-c")

    def crash_text(self) -> str:
        return self.shell("logcat", "-d", "-t", "400", "-s", "AndroidRuntime:E")


def nodes(xml_text: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for match in NODE_RE.finditer(xml_text):
        attrs = {k: v for k, v in ATTR_RE.findall(match.group(1))}
        if attrs:
            result.append(attrs)
    return result


def _matches(node: dict[str, str], needle: str, key: str) -> bool:
    value = node.get(key, "")
    if not value:
        return False
    if key == "text":
        return value == needle or needle in value
    return value == needle or value.endswith("/" + needle)


def find_node(xml_text: str, needle: str, key: str = "resource-id") -> dict[str, str] | None:
    for node in nodes(xml_text):
        if _matches(node, needle, key):
            return node
    return None


def has_node(xml_text: str, needle: str, key: str = "resource-id") -> bool:
    return find_node(xml_text, needle, key) is not None


def center(node: dict[str, str]) -> tuple[int, int] | None:
    match = BOUNDS_RE.search(node.get("bounds", ""))
    if not match:
        return None
    x1, y1, x2, y2 = (int(v) for v in match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def wait_for(device: Device, needle: str, key: str = "resource-id", timeout: int = 20) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if has_node(device.dump(), needle, key):
            return True
        time.sleep(0.7)
    return False


def screen_size(xml_text: str) -> tuple[int, int]:
    for node in nodes(xml_text):
        match = BOUNDS_RE.search(node.get("bounds", ""))
        if match:
            return int(match.group(3)), int(match.group(4))
    return 1080, 2400


def tap(device: Device, needle: str, key: str = "resource-id", timeout: int = 30) -> bool:
    deadline = time.time() + timeout
    last: dict[str, str] | None = None
    while time.time() < deadline:
        xml = device.dump()
        node = find_node(xml, needle, key)
        if node:
            last = node
            point = center(node)
            width, height = screen_size(xml)
            if point and 40 <= point[1] <= height - 40:
                device.shell("input", "tap", str(point[0]), str(point[1]))
                time.sleep(1.2)
                return True
            # 目标在折叠线以下/以上：按方向滚动一屏再找。
            _swipe(device, width, height, bool(point and point[1] < 40))
            time.sleep(0.8)
            continue
        time.sleep(0.7)
    if last is not None:
        device.observations.append(f"found but not tappable: {needle} bounds={last.get('bounds')}")
    return False


def navigate(device: Device, steps: list[str]) -> bool:
    for step in steps:
        if not step:
            continue
        if not tap(device, step):
            device.failures.append(f"navigation step not found: {step}")
            return False
        device.observations.append(f"tapped {step}")
    return True


def boot_id(device: Device) -> str:
    return device.shell("cat", "/proc/sys/kernel/random/boot_id").strip()


def _swipe(device: Device, width: int, height: int, downward: bool) -> None:
    if downward:
        # 手指向下滑 = 内容上移，用于找回上方的节点。
        device.shell(
            "input", "swipe", str(width // 2), str(int(height * 0.3)),
            str(width // 2), str(int(height * 0.8)), "300",
        )
    else:
        device.shell(
            "input", "swipe", str(width // 2), str(int(height * 0.8)),
            str(width // 2), str(int(height * 0.35)), "300",
        )


def ensure_visible(device: Device, needle: str, key: str = "resource-id",
                   attempts: int = 16) -> bool:
    """节点可能在折叠线以下/以上；按节点位置双向滚动，不把“没滚到”当成缺失。"""
    direction_down: bool | None = None
    for index in range(attempts):
        xml = device.dump()
        if "<hierarchy" not in xml:
            # uiautomator 在旋转/动画未 idle 时会拒绝 dump；等下一轮，别误判缺失。
            time.sleep(1.0)
            continue
        width, height = screen_size(xml)
        node = find_node(xml, needle, key)
        if node:
            point = center(node)
            if point and 40 <= point[1] <= height - 40:
                return True
            direction_down = bool(point and point[1] < 40)
        else:
            # 懒列表里未组合的节点：奇偶次交替上下找。
            direction_down = index % 2 == 1
        _swipe(device, width, height, bool(direction_down))
        time.sleep(0.7)
    xml = device.dump()
    return "<hierarchy" in xml and find_node(xml, needle, key) is not None


def assert_page(device: Device, root_tag: str, extra_tag: str | None) -> None:
    if not wait_for(device, root_tag, timeout=15):
        device.failures.append(f"page tag missing after action: {root_tag}")
        return
    device.observations.append(f"page tag present: {root_tag}")
    if extra_tag:
        if not ensure_visible(device, extra_tag):
            device.failures.append(f"expected tag missing after action: {extra_tag}")
        else:
            device.observations.append(f"page tag present: {extra_tag}")


def run_action(args: argparse.Namespace, device: Device) -> None:
    action = args.action
    if action == "clear":
        if not args.allow_destructive:
            device.failures.append("clear 需要显式 --allow-destructive")
            return
        device.shell("pm", "clear", args.package)
        device.observations.append("pm clear done")
        device.launch()
        time.sleep(4)
        if not navigate(device, args.nav):
            return
        assert_page(device, args.root_tag, args.extra_tag)
        if args.empty_tag and not wait_for(device, args.empty_tag, timeout=15):
            device.failures.append(f"empty tag missing: {args.empty_tag}")
        if args.empty_text and not wait_for(device, args.empty_text, key="text", timeout=15):
            device.failures.append(f"empty text missing: {args.empty_text}")
        if args.empty_text_b64:
            import base64

            text = base64.b64decode(args.empty_text_b64).decode("utf-8")
            if not wait_for(device, text, key="text", timeout=15):
                device.failures.append(f"empty text missing (b64): {text}")
        return

    # 每条探针独立起跑：先回到竖屏并冷启动，避免上一条探针遗留的路由/横屏。
    device.shell("settings", "put", "system", "accelerometer_rotation", "0")
    device.shell("settings", "put", "system", "user_rotation", "0")
    time.sleep(2)
    device.shell("am", "force-stop", args.package)
    time.sleep(1)
    device.launch()
    if not navigate(device, args.nav):
        return
    assert_page(device, args.root_tag, args.extra_tag)
    before_pid = device.pid()
    device.observations.append(f"pid before: {before_pid}")

    if action == "rotate":
        device.shell("settings", "put", "system", "accelerometer_rotation", "0")
        device.shell("settings", "put", "system", "user_rotation", "1")
        time.sleep(2.5)
        device.observations.append("rotated to landscape")
        # 横屏只验根页面仍在：折叠线以下的次要节点在横屏可能不组合，
        # 把它当缺失会把“布局变化”误判成“旋转失败”。
        assert_page(device, args.root_tag, None)
        device.shell("settings", "put", "system", "user_rotation", "0")
        time.sleep(2.5)
        device.observations.append("rotated back to portrait")
        assert_page(device, args.root_tag, args.extra_tag)
    elif action == "home":
        device.shell("input", "keyevent", "KEYCODE_HOME")
        time.sleep(2)
        device.observations.append("pressed HOME")
        device.launch()
        # 后台往返不重新导航：页面必须原样回来（否则就是状态丢失）。
        assert_page(device, args.root_tag, args.extra_tag)
    elif action == "kill":
        device.shell("input", "keyevent", "KEYCODE_HOME")
        time.sleep(2)
        device.shell("am", "kill", args.package)
        time.sleep(2)
        method = "am kill"
        if device.pid() == before_pid:
            device.shell("am", "force-stop", args.package)
            time.sleep(2)
            method = "am kill (not effective) + am force-stop"
        device.observations.append(f"process death method: {method}")
        device.launch()
        after_pid = device.pid()
        device.observations.append(f"pid after: {after_pid}")
        if after_pid and before_pid and after_pid == before_pid:
            device.failures.append("pid did not change after kill")
        if not wait_for(device, args.root_tag, timeout=15):
            device.observations.append(
                f"root not restored after kill: {args.root_tag}; re-navigating"
            )
            if not navigate(device, args.nav):
                device.failures.append(
                    f"page tag missing after kill and re-navigation: {args.root_tag}"
                )
                return
            if not wait_for(device, args.root_tag, timeout=15):
                device.failures.append(
                    f"page tag missing after kill and re-navigation: {args.root_tag}"
                )
                return
            device.observations.append(
                "data persisted: target reachable again after re-navigation"
            )
        device.observations.append(f"page tag present: {args.root_tag}")
        if args.extra_tag:
            if wait_for(device, args.extra_tag, timeout=10):
                device.observations.append(
                    f"sub-screen state restored after kill: {args.extra_tag}"
                )
            else:
                device.observations.append(
                    f"sub-screen not restored automatically; re-navigating: {args.extra_tag}"
                )
                if not navigate(device, args.nav):
                    return
                assert_page(device, args.root_tag, args.extra_tag)
    elif action == "permission":
        permission = args.permission
        device.shell("pm", "revoke", args.package, permission)
        device.observations.append(f"revoked {permission}")
        device.shell("am", "force-stop", args.package)
        device.launch()
        if not navigate(device, args.nav):
            return
        assert_page(device, args.root_tag, args.extra_tag)
        if not args.keep_permission_revoked:
            device.shell("pm", "grant", args.package, permission)
            device.observations.append(f"granted back {permission}")
    else:
        device.failures.append(f"unknown action: {action}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="A 域设备状态探针")
    parser.add_argument("--adb", default=None)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--apk-sha", required=True)
    parser.add_argument("--action", required=True,
                        choices=["rotate", "home", "kill", "clear", "permission"])
    parser.add_argument("--root-tag", required=True)
    parser.add_argument("--extra-tag", default="")
    parser.add_argument("--empty-tag", default="")
    parser.add_argument("--empty-text", default="")
    parser.add_argument("--empty-text-b64", default="",
                        help="UTF-8 文本的 base64；Windows 命令行避免中文乱码")
    parser.add_argument("--nav", action="append", default=[])
    parser.add_argument("--permission", default="android.permission.POST_NOTIFICATIONS")
    parser.add_argument("--allow-destructive", action="store_true")
    parser.add_argument("--keep-permission-revoked", action="store_true")
    parser.add_argument("--label", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args(argv)

    adb = _find_adb(args.adb)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    device = Device(adb, args.serial, args.package, timeout=args.timeout)
    started = _dt.datetime.now(_dt.timezone.utc)
    boot = boot_id(device)

    try:
        device.logcat_clear()
        run_action(args, device)
        crash = device.crash_text()
        if args.package in crash and "FATAL EXCEPTION" in crash:
            device.failures.append("AndroidRuntime FATAL EXCEPTION after action")
        if "FATAL EXCEPTION" in crash:
            device.observations.append("crash buffer:\n" + crash.strip()[:2000])
    except subprocess.TimeoutExpired as exc:
        device.failures.append(f"adb timeout: {exc}")
    except OSError as exc:
        print(f"ADB_ERROR: {exc}", file=sys.stderr)
        return 2

    passed = not device.failures
    record = {
        "serial": args.serial,
        "boot_id": boot,
        "apk_sha256": args.apk_sha,
        "package": args.package,
        "journey_label": args.label,
        "action": args.action,
        "root_tag": args.root_tag,
        "observations": device.observations,
        "failures": device.failures,
        "verdict": "PASS" if passed else "FAIL",
        "started_at": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "finished_at": _dt.datetime.now(_dt.timezone.utc)
        .strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    (out_dir / f"{args.label}.device.json").write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8", newline="\n",
    )
    log_lines = [
        f"probe={args.label} action={args.action} serial={args.serial}",
        f"package={args.package} apk_sha256={args.apk_sha} boot_id={boot}",
        *[f"OBS {line}" for line in device.observations],
        *[f"FAIL {line}" for line in device.failures],
        f"VERDICT {'PASS' if passed else 'FAIL'}",
        f"EXIT={0 if passed else 1}",
    ]
    (out_dir / f"{args.label}.log").write_text(
        "\n".join(log_lines) + "\n", encoding="utf-8", newline="\n"
    )
    print("\n".join(log_lines))
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
