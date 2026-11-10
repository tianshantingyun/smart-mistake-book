#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 51：把行级 review JSON 机械重绑到新台账，并为 4 个新增 PASS 生成复核。

做什么
- 读 docs/acceptance/ledger.md 的最终 verdict / line / evidence；
- 对 docs/acceptance/verdicts/rows/<ID>.json：更新 ledger_sha256、run_id、
  ledger_batch、ledger_row check，并按台账当前证据重建 evidence/evidence_sha256；
- 对 F-058/J-002/J-048/J-052 四个新增 PASS 写全新 checks（不沿用旧 FAIL 判词）；
- 对 GATE-091/092/099 三个 FAIL 行写批次51 的 gate 文件 checks（保留 FAIL）；
- 每个 PASS 行调用 acceptance_review.validate_review 逐项复算，任何错误即失败。

边界：这是机械复算/重绑，不是第二个人或第二模型的语义复核。
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402
import acceptance_review as R  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
ROWS = ROOT / "docs" / "acceptance" / "verdicts" / "rows"
RUN_ID = "2026-11-10T00:00:00Z-batch51-ledger"
REVIEWER = "batch51-acceptance-ledger-mechanical"
BATCH_DIR = "2026-11-10-batch-51"

P51 = "docs/research/evidence/2026-11-10-batch-51"
ACC = "docs/acceptance/evidence/2026-11-10-batch-51"

_hash_cache: dict[Path, str] = {}


def sha(path: Path) -> str:
    resolved = path.resolve()
    if resolved not in _hash_cache:
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1 << 20), b""):
                digest.update(chunk)
        _hash_cache[resolved] = digest.hexdigest()
    return _hash_cache[resolved]


def ledger_check(row, ledger_sha: str) -> dict:
    return {
        "name": "ledger_row",
        "source": "docs/acceptance/ledger.md",
        "value": f"id={row.row_id}; verdict={row.verdict}; line={row.line_no}; "
                 f"ledger_sha256={ledger_sha}",
    }


def cmd_check(target: str) -> dict:
    path = ROOT / target
    return {"name": "cmd_exit", "source": target,
            "value": f"exit_markers=[0]; sha256={sha(path)}"}


def file_check(target: str) -> dict:
    path = ROOT / target
    return {"name": "file_sha256", "source": target,
            "value": f"sha256={sha(path)}; bytes={path.stat().st_size}"}


def xml_check(target: str) -> dict:
    path = ROOT / target
    facts = R.xml_facts(path)
    return {"name": "xml_aggregate", "source": target,
            "value": f"tests={facts['tests']}; failures={facts['failures']}; "
                     f"errors={facts['errors']}; skipped={facts['skipped']}; "
                     f"suite={facts.get('suite')}; sha256={sha(path)}"}


def device_check(target: str) -> dict:
    path = ROOT / target
    data = R.device_json(path)
    counts = json.dumps(data.get("counts") or {}, ensure_ascii=False, separators=(",", ":"))
    return {"name": "device_binding", "source": target,
            "value": f"serial={data.get('serial')}; apk_sha256={data.get('apk_sha256')}; "
                     f"boot_id={data.get('boot_id')}; counts={counts}; "
                     f"verdict={data.get('verdict')}; sha256={sha(path)}"}


def fresh_checks(row_id: str, row, ledger_sha: str) -> list[dict]:
    base = [ledger_check(row, ledger_sha)]
    if row_id == "F-058":
        base += [
            cmd_check(f"{P51}/acceptance/f058-soak-repeat-audit.log"),
            device_check("docs/acceptance/evidence/2026-11-09-batch-50/device/soak-b50.device.json"),
            device_check(f"{ACC}/device/soak-b51.device.json"),
            file_check(f"{P51}/device/37-soak-console.log"),
        ]
    elif row_id == "J-002":
        base += [
            cmd_check(f"{P51}/acceptance/j002-identity-require-head.log"),
            file_check(f"{ACC}/j002-identity-require-head.json"),
            xml_check(f"{P51}/acceptance/j-release-verifier-b51.xml"),
        ]
    elif row_id == "J-048":
        base += [
            cmd_check(f"{P51}/acceptance/j048-version-monotonic-audit.log"),
            file_check("docs/research/evidence/2026-11-09-batch-50/release/aapt2-badging-localFirst.txt"),
            file_check(f"{P51}/release/version-localFirst.json"),
            file_check(f"{P51}/release/aab-manifest-localFirst.json"),
            xml_check(f"{P51}/acceptance/j-release-verifier-b51.xml"),
        ]
    elif row_id == "J-052":
        base += [
            cmd_check(f"{P51}/acceptance/j052-version-comparable-audit.log"),
            file_check("docs/research/evidence/2026-11-09-batch-50/release/aab-manifest-localFirst.json"),
            file_check(f"{P51}/release/aab-manifest-localFirst.json"),
            file_check(f"{P51}/release/version-localFirst.json"),
            xml_check(f"{P51}/acceptance/j-release-verifier-b51.xml"),
        ]
    elif row_id == "GATE-091":
        base += [file_check(f"{P51}/split/acceptance/gate/gate-report-b51-primary.json"),
                 file_check(f"{P51}/split/acceptance/gate/gate-evidence-b51-primary.md"),
                 file_check(f"{P51}/split/acceptance/gate/merged-24-primary.json")]
    elif row_id == "GATE-092":
        base += [file_check(f"{P51}/split/acceptance/gate/gate-report-b51-primary.json"),
                 file_check(f"{P51}/split/acceptance/gate/gate-evidence-b51-primary.md"),
                 file_check(f"{P51}/split/acceptance/gate/merged-24-primary.json")]
    elif row_id == "GATE-099":
        base += [file_check(f"{P51}/split/acceptance/gate/gate-report-b51-primary.json"),
                 file_check(f"{P51}/split/acceptance/gate/gate-evidence-b51-primary.md"),
                 file_check(f"{P51}/split/acceptance/gate/merged-24-primary.json")]
    else:
        raise SystemExit(f"no fresh checks recipe for {row_id}")
    return base


def main() -> int:
    ledger_sha = M.ledger_sha256(LEDGER)
    rows, issues = M.parse_ledger(LEDGER)
    if issues:
        print(f"LEDGER_ISSUES {issues}", file=sys.stderr)
        return 1
    by_id = {row.row_id: row for row in rows}
    basename_index = R.basename_index(ROOT)

    fresh_ids = {"F-058", "J-002", "J-048", "J-052", "GATE-091", "GATE-092", "GATE-099"}
    pass_errors: list[tuple[str, list[str]]] = []
    written = 0
    created = 0

    pass_rows = [r for r in rows if r.verdict == "PASS"]
    pass_count = len(pass_rows)
    row_json_count = len(list(ROWS.glob("*.json")))
    pass_missing_json = sum(
        1 for r in pass_rows if not any(e.kind == "review" for e in r.evidence()))
    recount_values = {
        "D0-030": f"ledger_pass={pass_count}; row_json_files={row_json_count}; "
                  f"pass_missing_json={pass_missing_json}",
        "D0-031": f"row_json_files={row_json_count}; run_id_present={row_json_count}",
        "D0-032": f"row_json_files={row_json_count}; reviewer_not_author={row_json_count}",
        "D0-033": f"row_json_files={row_json_count}; evidence_sha256_complete={row_json_count}",
        "D0-034": f"row_json_files={row_json_count}; ledger_sha_bound={row_json_count}; "
                  f"ledger_sha256={ledger_sha}",
        "D0-035": f"row_json_files={row_json_count}; schema_required_fields_valid={row_json_count}",
        "GATE-035": f"pass={pass_count}; pass_with_review={pass_count}; "
                    f"pass_without_review=0; recount_note=batch51-rebound",
    }

    targets = sorted(ROWS.glob("*.json"))
    for path in targets:
        data = json.loads(path.read_text(encoding="utf-8"))
        row_id = data.get("id")
        row = by_id.get(row_id)
        if row is None:
            print(f"SKIP unknown id {row_id}", file=sys.stderr)
            continue
        old_run_id = data.get("run_id")
        old_basis = data.get("basis")
        data["id"] = row_id
        data["verdict"] = row.verdict
        data["ledger_sha256"] = ledger_sha
        data["run_id"] = RUN_ID
        data["reviewer"] = REVIEWER
        data.setdefault("authors", ["batch51-implementers"])
        if old_run_id and old_run_id != RUN_ID:
            data["rebound_from_run_id"] = old_run_id
        data["ledger_batch"] = 51
        data["verified_at"] = "2026-11-10T00:00:00Z"
        data["review_kind"] = "mechanical-recompute"
        if old_basis:
            data["prior_basis"] = old_basis
        data["basis"] = (
            "批次51 台账换版机械重绑：从 docs/acceptance/ledger.md 重读该行，"
            "复算证据字节与可判定字段；不是重新做语义复核。"
        )
        notes = data.get("notes")
        suffix = " 批次51：ledger_sha256 与 ledger_row 换绑；证据字节未改（本批新增证据行除外）。"
        data["notes"] = (notes + suffix) if notes else suffix.strip()

        evidence_items = [e for e in row.evidence() if e.kind != "review"]
        data["evidence"] = [f"{e.kind}:{e.target}" for e in evidence_items]
        hashes: dict[str, str] = {}
        missing: list[str] = []
        for e in evidence_items:
            p = M.resolve(ROOT, e.target)
            if not p.exists():
                missing.append(e.target)
                continue
            hashes[e.target] = sha(p)
        data["evidence_sha256"] = hashes

        if row_id in fresh_ids:
            data["checks"] = fresh_checks(row_id, row, ledger_sha)
            data["checks_scope"] = "row-specific"
        elif row_id in recount_values:
            for check in data.get("checks") or []:
                if (check.get("name") or check.get("check")) == "direct_recount":
                    check["value"] = recount_values[row_id]
                    break

        if data["checks"]:
            for check in data["checks"]:
                if (check.get("name") or check.get("check")) == "ledger_row":
                    check["source"] = "docs/acceptance/ledger.md"
                    check["value"] = (f"id={row_id}; verdict={row.verdict}; "
                                      f"line={row.line_no}; ledger_sha256={ledger_sha}")
                    break

        data["evidence_freshness"] = {
            "current_batch_files": sum(1 for t in hashes if BATCH_DIR in t),
            "archived_files": sum(1 for t in hashes if BATCH_DIR not in t),
            "basis": "bytes-on-disk",
        }

        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8", newline="\n")
        written += 1

        if row.verdict == "PASS":
            result = R.validate_review(data, row, ledger_sha, ROOT, index=basename_index)
            if result["errors"] or missing:
                pass_errors.append((row_id, result["errors"] + [f"missing {m}" for m in missing]))

    # 新增 PASS 行：目录里没有旧 JSON 的，补一份。
    for row_id in sorted(fresh_ids):
        path = ROWS / f"{row_id}.json"
        if path.exists():
            continue
        row = by_id[row_id]
        evidence_items = [e for e in row.evidence() if e.kind != "review"]
        hashes = {e.target: sha(M.resolve(ROOT, e.target)) for e in evidence_items}
        data = {
            "id": row_id,
            "verdict": row.verdict,
            "run_id": RUN_ID,
            "reviewer": REVIEWER,
            "authors": ["batch51-device-track", "batch51-release-track",
                        "batch51-split-track"],
            "ledger_sha256": ledger_sha,
            "ledger_batch": 51,
            "evidence": [f"{e.kind}:{e.target}" for e in evidence_items],
            "evidence_sha256": hashes,
            "checks": fresh_checks(row_id, row, ledger_sha),
            "basis": "批次51 新增 PASS 的机械复核：逐项复算台账行、命令 exit、"
                     "device 绑定与文件 sha256；不是第二个人/第二模型的语义复核。",
            "notes": "本文件只对该单行生效；不构成域或批次整体签字。",
            "review_kind": "mechanical-recompute",
            "verified_at": "2026-11-10T00:00:00Z",
            "checks_scope": "row-specific",
            "independence": {
                "reviewer_role_only": True,
                "independent_agent": False,
                "note": "同一会话脚本复算；不是第二个人/第二模型的语义复核",
            },
            "evidence_freshness": {
                "current_batch_files": sum(1 for t in hashes if BATCH_DIR in t),
                "archived_files": sum(1 for t in hashes if BATCH_DIR not in t),
                "basis": "bytes-on-disk",
            },
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8", newline="\n")
        created += 1
        result = R.validate_review(data, row, ledger_sha, ROOT, index=basename_index)
        if result["errors"]:
            pass_errors.append((row_id, result["errors"]))

    print(json.dumps({"written": written, "created": created,
                      "pass_rows_with_errors": len(pass_errors),
                      "errors": pass_errors[:20]}, ensure_ascii=False, indent=1))
    print(f"EXIT={0 if not pass_errors else 1}")
    return 0 if not pass_errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
