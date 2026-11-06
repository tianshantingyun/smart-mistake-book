#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KD-76 A6 预注册文件：在两批开跑**之前**写死口径，事后不可改。

批次 43 / WS2 item 11（协议 `docs/research/2026-11-05-kd76-a6-protocol.md` §4.1）。

## 它消灭的具体失败

1. **跑完看结果再改口径**（改 `minSeparationMinutes`、放宽门、加替换批）：`write`
   拒绝覆盖已存在的 plan；`validate` 拒绝 `minSeparationMinutes < 10`、
   `maxReplacementBatches > 1`、门/估计量/模式与冻结值不符。
2. **两批绑到不同构建/设备/安装却声称"同构建"**：plan 里的
   `expectedAppApkSha256` / `expectedTestApkSha256` / `expectedDeviceGenerationId` /
   `expectedInstallGenerationId` 来自 `kd76_identity.py` 的宿主实测，两批 manifest
   必须与它三方相等（判词在 `kd76_a6_verify.py`）。
3. **plan 被悄悄替换**：`write` 输出 `sha256`，两批 manifest 记 `a6PlanSha256`；
   composer 重算并比对。

## 明确不做

- 不安装、不重启、不跑设备；只读身份 + 写一个文本文件。
- 不提供"编辑已有 plan"的入口（预注册的意义就在于此）。

用法：
    python -I tools/ci/a6_plan.py write --identity-json build/kd76-a6/identity.json \
        --out build/kd76-runs/a6/a6-plan.txt --plan-id 2026-11-05T10-00Z-ab12
    python -I tools/ci/a6_plan.py validate --plan build/kd76-runs/a6/a6-plan.txt
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from summarize_kd76 import GATE_MILLIS, MAIN_ROUNDS  # noqa: E402

PLAN_VERSION = 1
MIN_SEPARATION_FLOOR_MINUTES = 10
MAX_REPLACEMENT_BATCHES = 1

REQUIRED_IDENTITY = (
    "installedAppSha256",
    "installedTestSha256",
    "deviceGenerationId",
    "installGenerationId",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_kv(path: Path) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if line and "=" in line:
            key, value = line.split("=", 1)
            fields[key.strip()] = value.strip()
    return fields


def render_plan(identity: dict[str, str], plan_id: str, min_separation: int, written_utc: str) -> str:
    return "\n".join(
        [
            f"a6PlanVersion={PLAN_VERSION}",
            f"planId={plan_id}",
            f"writtenUtc={written_utc}",
            "batchIds=A,B",
            "requiredBatches=2",
            f"roundsPerBatch={MAIN_ROUNDS}",
            "estimator=nearest-rank",
            f"gateMillis={GATE_MILLIS}",
            "mode=reboot",
            "settleSeconds=0",
            f"minSeparationMinutes={min_separation}",
            f"maxReplacementBatches={MAX_REPLACEMENT_BATCHES}",
            f"expectedAppApkSha256={identity['installedAppSha256']}",
            f"expectedTestApkSha256={identity['installedTestSha256']}",
            f"expectedDeviceGenerationId={identity['deviceGenerationId']}",
            f"expectedInstallGenerationId={identity['installGenerationId']}",
            "allowReinstall=false",
            f"serial={identity.get('serial', '')}",
            f"avdName={identity.get('avdName', '')}",
            f"emulatorInstanceId={identity.get('emulatorInstanceId', '')}",
            "",
        ]
    )


def validate(plan: dict[str, str]) -> list[str]:
    problems: list[str] = []
    expected = {
        "a6PlanVersion": str(PLAN_VERSION),
        "requiredBatches": "2",
        "roundsPerBatch": str(MAIN_ROUNDS),
        "estimator": "nearest-rank",
        "gateMillis": str(GATE_MILLIS),
        "mode": "reboot",
        "settleSeconds": "0",
        "allowReinstall": "false",
    }
    for key, value in expected.items():
        if key not in plan:
            problems.append(f"missing {key}")
        elif plan[key] != value:
            problems.append(f"{key}={plan[key]!r} expected {value!r}")
    separation = plan.get("minSeparationMinutes", "")
    if not separation.isdigit():
        problems.append("minSeparationMinutes must be an integer")
    elif int(separation) < MIN_SEPARATION_FLOOR_MINUTES:
        problems.append(
            f"minSeparationMinutes={separation} < hard floor {MIN_SEPARATION_FLOOR_MINUTES}"
        )
    replacement = plan.get("maxReplacementBatches", "")
    if not replacement.isdigit():
        problems.append("maxReplacementBatches must be an integer")
    elif int(replacement) > MAX_REPLACEMENT_BATCHES:
        problems.append(f"maxReplacementBatches={replacement} > {MAX_REPLACEMENT_BATCHES}")
    for key in (
        "expectedAppApkSha256",
        "expectedTestApkSha256",
        "expectedDeviceGenerationId",
        "expectedInstallGenerationId",
    ):
        value = plan.get(key, "")
        if len(value) != 64 or any(char not in "0123456789abcdef" for char in value):
            problems.append(f"{key} is not a 64-hex digest")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="KD-76 A6 pre-registration plan")
    sub = parser.add_subparsers(dest="command", required=True)

    write_parser = sub.add_parser("write", help="write a new plan (refuses to overwrite)")
    write_parser.add_argument("--identity-json", required=True)
    write_parser.add_argument("--out", required=True)
    write_parser.add_argument("--plan-id", default="")
    write_parser.add_argument("--min-separation-minutes", type=int, default=30)

    validate_parser = sub.add_parser("validate", help="validate an existing plan")
    validate_parser.add_argument("--plan", required=True)

    args = parser.parse_args(argv)

    if args.command == "validate":
        plan_path = Path(args.plan)
        if not plan_path.is_file():
            print(f"a6_plan: plan not found: {plan_path}", file=sys.stderr)
            return 2
        problems = validate(read_kv(plan_path))
        if problems:
            for problem in problems:
                print(f"a6_plan: {problem}", file=sys.stderr)
            return 2
        print(f"OK sha256={sha256_file(plan_path)}")
        return 0

    if args.min_separation_minutes < MIN_SEPARATION_FLOOR_MINUTES:
        print(
            f"a6_plan: --min-separation-minutes={args.min_separation_minutes} < hard floor "
            f"{MIN_SEPARATION_FLOOR_MINUTES}",
            file=sys.stderr,
        )
        return 2

    identity_path = Path(args.identity_json)
    if not identity_path.is_file():
        print(f"a6_plan: identity json not found: {identity_path}", file=sys.stderr)
        return 2
    identity = json.loads(identity_path.read_text(encoding="utf-8"))
    missing = [key for key in REQUIRED_IDENTITY if not identity.get(key)]
    if missing:
        print(f"a6_plan: identity json missing {missing}", file=sys.stderr)
        return 2

    out_path = Path(args.out)
    if out_path.exists():
        print(
            f"a6_plan: refusing to overwrite an existing plan: {out_path} "
            "(pre-registration must not be edited after the fact)",
            file=sys.stderr,
        )
        return 2
    out_path.parent.mkdir(parents=True, exist_ok=True)

    plan_id = args.plan_id or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%MZ")
    written = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    text = render_plan(identity, plan_id, args.min_separation_minutes, written)
    out_path.write_text(text, encoding="utf-8")

    problems = validate(read_kv(out_path))
    if problems:
        out_path.unlink()
        for problem in problems:
            print(f"a6_plan: {problem}", file=sys.stderr)
        return 2
    print(f"planId={plan_id}")
    print(f"sha256={sha256_file(out_path)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
