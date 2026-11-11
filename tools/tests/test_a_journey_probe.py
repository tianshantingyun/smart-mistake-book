# -*- coding: utf-8 -*-
"""a_journey_probe 的 uiautomator 解析单测（批次 44 A 域设备探针）。

消灭的具体失败：探针把 `resource-id` 匹配写成子串包含，`root_review` 会
误命中 `root_review_extra`；或把 bounds 解析错导致点到屏幕外。这里锁定
精确/后缀匹配与中心点计算。
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "ci"))

import a_journey_probe as P  # noqa: E402


DUMP = """\
<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="p" bounds="[0,0][1080,2400]">
    <node index="0" text="复习" resource-id="root_review" class="android.view.View" package="p" bounds="[0,100][1080,2200]" />
    <node index="1" text="错题本" resource-id="nav_library" class="android.view.View" package="p" bounds="[270,2200][540,2400]" />
    <node index="2" text="复习" resource-id="root_review_extra" class="android.view.View" package="p" bounds="[0,0][10,10]" />
  </node>
</hierarchy>
"""


def test_nodes_are_extracted_without_xml_entities():
    parsed = P.nodes(DUMP)
    assert len(parsed) == 4
    assert parsed[1]["resource-id"] == "root_review"


def test_exact_and_suffix_match_do_not_hit_longer_ids():
    assert P.find_node(DUMP, "root_review")["resource-id"] == "root_review"
    assert P.find_node(DUMP, "root_review_extra")["resource-id"] == "root_review_extra"
    assert P.has_node(DUMP, "nav_library")
    assert not P.has_node(DUMP, "nav_lib")


def test_center_is_bounds_midpoint():
    node = P.find_node(DUMP, "nav_library")
    assert P.center(node) == (405, 2300)


def test_text_key_is_supported():
    assert P.has_node(DUMP, "错题本", key="text")
    assert not P.has_node(DUMP, "不存在", key="text")
