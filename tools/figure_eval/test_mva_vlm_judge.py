# -*- coding: utf-8 -*-
"""VLM 判官（C 通道）的离线单测：盲法、通道边界、值规范化、JSON 提取。

不触网：`call_vision` 不在这些用例的覆盖范围（真实调用属设备/门执行证据）。
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import mva_vlm_judge as judge  # noqa: E402


def _task() -> dict:
    return {
        "task_id": "BLD-MECHPHASE-007",
        "group": "mechanics_phases",
        "problem_statement": "物块静止在倾角 30° 的斜面上。画出重力 G、支持力 N、摩擦力 f。",
        "fact_expectations": [
            {"kind": "direction", "key": "friction_direction", "expected": "along_incline_up", "critical": True},
            {"kind": "direction", "key": "gravity_direction", "expected": "down", "critical": True},
            {"kind": "numeric", "key": "arm_length_ratio", "expected": 0.5, "tolerance": 1e-9, "critical": True},
            {"kind": "label_set", "key": "force_labels", "expected": ["重力G", "支持力N", "摩擦力f"], "critical": True},
        ],
    }


def test_judgeable_keys_are_c_channel_only() -> None:
    # numeric A2（arm_length_ratio）是 A 通道（tolerance 1e-9），不得交给视觉判官；
    # label_set 是 A1；只有 direction/ordering/count/label_absent 进 C。
    assert judge.judgeable_keys(_task()) == ["friction_direction", "gravity_direction"]


def test_prompt_never_carries_expected_values_or_task_identity() -> None:
    task = _task()
    prompt = judge.build_prompt(task["problem_statement"], ["friction_direction", "gravity_direction"], task)
    # 身份与分组不进提示词（盲法）。
    assert "BLD-MECHPHASE-007" not in prompt
    assert "mechanics_phases" not in prompt
    # 预期值不得以"expected=..." 形式出现；方向只能作为封闭选项词表出现。
    assert "expected" not in prompt.lower()
    assert "along_incline_up" in prompt  # 选项词表允许
    assert "0.5" not in prompt  # 数值事实的预期值不进口
    assert "重力G" not in prompt  # label_set 预期不进口


def test_direction_normalization_accepts_chinese_and_enum() -> None:
    assert judge._normalize_direction("down") == "down"
    assert judge._normalize_direction("竖直向下") == "down"
    assert judge._normalize_direction("沿斜面向上") == "along_incline_up"
    assert judge._normalize_direction("竖直向上") == "up"
    assert judge._normalize_direction("斜向上") is None


def test_ordering_normalization_strips_spaces_and_normalizes_fullwidth() -> None:
    assert judge._normalize_ordering(["u = 0", "u＝1/2", "u=1"]) == ["u=0", "u=1/2", "u=1"]
    assert judge._normalize_ordering("u=0") is None


def test_label_absent_normalization_maps_synonyms() -> None:
    assert judge._normalize_label_absent(["红勾", "对勾", "波浪"]) == ["红笔勾", "对号", "波浪线"]
    assert judge._normalize_label_absent([]) == []


def test_extract_json_handles_fenced_and_embedded_objects() -> None:
    assert judge._extract_json('```json\n{"facts": {"a": 1}}\n```') == {"facts": {"a": 1}}
    assert judge._extract_json('前缀 {"facts": {"b": [1, 2]}} 后缀') == {"facts": {"b": [1, 2]}}
    assert judge._extract_json("not json") is None


def test_normalize_facts_drops_unanswered_and_wrong_typed_values() -> None:
    parsed = {"facts": {"friction_direction": "沿斜面向上", "gravity_direction": 3, "arm_length_ratio": 0.5}}
    normalized = judge.normalize_facts(
        parsed, ["friction_direction", "gravity_direction"], {
            "friction_direction": "direction", "gravity_direction": "direction"})
    assert normalized == {"friction_direction": "along_incline_up"}


def test_calibration_outcome_absence_correct_is_not_a_wrong_value() -> None:
    # label_absent 的正确图：truth=[]（无手写）而 gate_expected 是禁止清单；
    # 不能用 truth != gate_expected 推导"注入错值"，否则正确图会被记成失败。
    outcome = judge.calibration_outcome([], ["红笔勾", "对号", "波浪线"], [], "label_absent", injected=False)
    assert outcome["reads_image_correctly"] is True
    assert outcome["gate_check_accepted"] is True
    assert outcome["injected_wrong_value_caught"] is True


def test_calibration_outcome_injected_wrong_value_must_be_caught() -> None:
    # 图上真的画了红勾，判官如实读出；门函数必须拒绝（禁写内容出现）。
    outcome = judge.calibration_outcome(
        ["红笔勾"], ["红笔勾", "对号", "波浪线"], ["红笔勾"], "label_absent", injected=True)
    assert outcome["reads_image_correctly"] is True
    assert outcome["gate_check_accepted"] is False
    assert outcome["injected_wrong_value_caught"] is True
    # 判官若没读出图上内容（got=None），注入错值不算"被抓到"。
    missed = judge.calibration_outcome(["down"], "up", None, "direction", injected=True)
    assert missed["reads_image_correctly"] is False
    assert missed["injected_wrong_value_caught"] is False
