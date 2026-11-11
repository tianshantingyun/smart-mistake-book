#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""口径 v3 同一样本重算（只读；只作对照，不作 acceptance）。

运行（仓库根）：
    python -I docs/research/evidence/2026-11-10-batch-51/split/recompute_caliber_v3.py

输入是仓内既有导出与金标；本脚本不写任何文件，结果只打印到 stdout。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(REPO / "tools" / "split_golden"))
import measure_split_accuracy as M  # noqa: E402

CASES = (
    ("batch48_all_24", "docs/research/evidence/2026-11-07-batch-48/splitter/"
                       "app-split-golden-v2-final.json",
     "tools/split_golden/tables/split_golden_v2.json"),
    ("batch48_acceptance_8", "docs/research/evidence/2026-11-07-batch-48/splitter/"
                             "acceptance-once-01-export.json",
     "docs/research/evidence/2026-11-07-batch-48/splitter/golden-v2-acceptance-slice.json"),
    ("batch50_all_24", "docs/research/evidence/2026-11-09-batch-50/split-gate/"
                       "app-split-b50-final.json",
     "tools/split_golden/tables/split_golden_v2.json"),
    ("batch50_acceptance_8", "docs/research/evidence/2026-11-09-batch-50/split-gate/exports/"
                             "acceptance-b50-01-export.json",
     "docs/research/evidence/2026-11-09-batch-50/split-gate/tables/"
     "golden-v2-acceptance-slice.json"),
    ("device_v1_7", "build/split-eval/app-split.json",
     "tools/split_golden/tables/split_golden_v1.json"),
    ("device_v2_24", "build/split-eval/app-split.json",
     "tools/split_golden/tables/split_golden_v2.json"),
)


def main() -> int:
    summary = {}
    for name, app_rel, golden_rel in CASES:
        app_path = REPO / app_rel
        golden_path = REPO / golden_rel
        if not app_path.is_file() or not golden_path.is_file():
            print(f"{name}: MISSING input (app={app_path.is_file()}, "
                  f"golden={golden_path.is_file()})")
            continue
        golden_pages = M.load_golden(golden_path)
        report = M.build_report(golden_path, golden_pages, app_path)
        rows = report["pages"]
        summary[name] = {
            "app": app_rel,
            "golden": golden_rel,
            "decisionSource": report["decisionSource"],
            "caliber": report["caliber"],
            "countExactV2": report["countExactV2"],
            "countExactV3": report["metrics"]["countExact"],
            "fragment": report["metrics"]["fragment"],
            "abstain": report["abstain"],
            "notEvaluable": report["notEvaluable"],
            "singleQuestionUnverifiedCount": report["singleQuestionUnverifiedCount"],
            "changedPagesV2": report["changedPagesV2"],
            "missingAppPages": report["missingAppPages"],
        }
        print(f"== {name}")
        print(f"   app={app_rel}")
        print(f"   golden={golden_rel}")
        print(f"   decisionSource={report['decisionSource']} caliber={report['caliber']} "
              f"singleQuestionUnverified={report['singleQuestionUnverifiedCount']}")
        old = report["countExactV2"]
        new = report["metrics"]["countExact"]
        print(f"   old v2 ① = {old['successes']}/{old['denominator']} = {old['value']:.4f}")
        print(f"   new v3 ① = {new['successes']}/{new['denominator']} = {new['value']:.4f}")
        frag = report["metrics"]["fragment"]
        print(f"   ③ = {frag['successes']}/{frag['denominator']} = {frag['value']:.4f}; "
              f"fragmented={frag['successes']}")
        print(f"   abstain={report['abstain']['count']} "
              f"pages={report['abstain']['pages']}")
        print(f"   notEvaluable={report['notEvaluable']['count']} "
              f"pages={report['notEvaluable']['pages']}")
        print(f"   changedPagesV2={report['changedPagesV2']}")
        print(f"   missingAppPages={report['missingAppPages']}")
        for row in rows:
            print(f"     {row['file']:<24} gold={row['goldQuestions']} "
                  f"app={row['appBoxes']:<2} class={row['pageClass']:<22} "
                  f"v2={'T' if row['consistentV2'] else 'F'} "
                  f"v3={'T' if row['consistent'] else 'F'} "
                  f"status={row['appOutcomeStatus']!r} decision={row['appDecision']!r}")
        print()
    print("JSON_SUMMARY")
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
