# -*- coding: utf-8 -*-
"""dense 向量资产门（`tools/dense_build/check_asset.py`）的**正反两侧**用例。

它盯的失败很具体：**包或词表内容变了、而 `.vec` 没跟着重生成**。这类失败在现有 23 门里
完全不可见（那些门只查包的自身契约），而端侧会拿"旧内容的向量"去比"新词条的文本"，
检索分数整体失真——所以门必须能红，且红得指得出是哪一处不一致。

- 正侧：真资产（仓库当前状态）⇒ 空 `failed`；
- 反侧 1：**包被改过**（在临时副本里加一个别名）⇒ `packSha256` 必须红；
- 反侧 2：**词表被改过** ⇒ `vocab:*` 必须红；
- 反侧 3：**.vec 头/行数与旁车不符** ⇒ `vectorHeader`/`vectorCount` 必须红。

反侧全部在临时副本上做（不碰真资产）。
"""
from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from dense_build import check_asset  # noqa: E402
from dense_build import dense_asset as D  # noqa: E402


class DenseAssetGateTest(unittest.TestCase):

    def setUp(self):
        self.vector = REPO.joinpath(*D.DENSE_DIR_RELATIVE.split("/")) / D.VECTOR_FILE_NAME
        self.sidecar = self.vector.with_name(self.vector.name + ".json")
        self.pack = REPO.joinpath(*D.PACK_RELATIVE.split("/"))
        self.vocab = REPO.joinpath(*D.VOCAB_RELATIVE.split("/"))
        self.tmp = Path(tempfile.mkdtemp(prefix="dense-gate-"))

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _failed_names(self, result):
        return sorted(check["name"] for check in result["failed"])

    def _tamper_pack(self, filename: str) -> Path:
        """复制一份包并把**第一条别名加到第一个非空 topic 的第一个知识点上**。

        包里有空 topic（0 个知识点），所以不能写死 `topics[0].knowledgePoints[0]`——
        首版就是这么写而 IndexError 的。
        """
        copy = self.tmp / filename
        payload = json.loads(self.pack.read_text(encoding="utf-8"))
        for subject in payload["subjects"]:
            for topic in subject["topics"]:
                if topic["knowledgePoints"]:
                    point = topic["knowledgePoints"][0]
                    point["aliases"] = list(point.get("aliases") or []) + ["新增别名-向量里没有"]
                    copy.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
                    return copy
        raise AssertionError("包里找不到任何非空 topic——测试夹具失效")

    def test_realAssetPassesAllChecks(self):
        """正侧：真资产必须全绿（任一红都说明资产与当前包/词表已经不同源）。"""
        result = check_asset.evaluate(REPO)
        self.assertTrue(result["ok"], "dense 资产门在真资产上应全绿，实际红：%r"
                        % [(c["name"], c["detail"]) for c in result["failed"]])
        names = [check["name"] for check in result["checks"]]
        for expected in ("packSha256", "vocab:vocab", "vocab:tokenizer", "vectorSha256",
                         "vectorHeader", "vectorCount", "vectorBytes", "layout", "layoutSha256"):
            self.assertIn(expected, names, "门漏掉了检查项 %s" % expected)

    def test_packChangedButVectorNotRegeneratedIsRed(self):
        """反侧 1：包内容变了（加一条别名）而向量没重生成 ⇒ `packSha256` 与 `layout` 必须红。"""
        pack_copy = self._tamper_pack("pack.json")
        self.assertNotEqual(D.sha256_file(pack_copy), D.sha256_file(self.pack),
                            "测试前提：改过的包 sha256 必须与原包不同")
        result = check_asset.evaluate(REPO, pack_path=pack_copy)
        self.assertFalse(result["ok"], "包内容变了，门必须红")
        failed = self._failed_names(result)
        self.assertIn("packSha256", failed)
        self.assertIn("layout", failed)

    def test_packAliasAddedIsCaughtByLayoutEvenIfHashesMatch(self):
        """反侧 1b：改包 + 把旁车哈希同步成新包 ⇒ 仍必须被 `layout` 抓住。

        这是这门存在的核心理由：光比哈希，可以"改包 + 改旁车哈希"骗过去；比 ids 与当前包
        布局才把"这份向量属于这一版包"钉死。
        """
        pack_copy = self._tamper_pack("pack2.json")
        sidecar_copy = self.tmp / "fake.json"
        faked = json.loads(self.sidecar.read_text(encoding="utf-8"))
        faked["packSha256"] = D.sha256_file(pack_copy)  # 把哈希"改对"——哈希层被绕过
        sidecar_copy.write_text(json.dumps(faked, ensure_ascii=False, indent=2), encoding="utf-8")
        result = check_asset.evaluate(REPO, pack_path=pack_copy, sidecar_path=sidecar_copy)
        self.assertFalse(result["ok"], "包布局变了（多一条别名）而向量没重生成：即便旁车哈希被同步，门也必须红")
        failed = self._failed_names(result)
        self.assertIn("layout", failed)
        self.assertNotIn("packSha256", failed, "这一例的前提是哈希层已被绕过，它不该红")

    # ---- 布局文本漂移（N1/N2/N3）：ids 与行数都不变，只有 `layoutSha256` 能红 ----
    # 冻结值绑定主包 sha `10ede1bf…`（预注册 §2.3）。若主包 sha 不是该值，期望值必须随
    # 预注册重新冻结，**不得**就地更新这三个值来"变绿"。

    def _first_non_empty_point(self, payload):
        for subject in payload["subjects"]:
            for topic in subject["topics"]:
                if topic["knowledgePoints"]:
                    return topic["knowledgePoints"][0]
        raise AssertionError("包里找不到任何非空 topic——测试夹具失效")

    def _evaluate_mutated_pack(self, payload, filename: str):
        """把改过的包落临时文件，旁车 `packSha256` **重钉**到改后包（模拟"改哈希绕过"）。

        返回 (result, mutated_layout_sha, mutated_ids_equal_baseline)。
        """
        pack_copy = self.tmp / filename
        pack_copy.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        sidecar_copy = self.tmp / (filename + ".sidecar.json")
        faked = json.loads(self.sidecar.read_text(encoding="utf-8"))
        faked["packSha256"] = D.sha256_file(pack_copy)  # 哈希层被"改对"——这正是本门要封堵的绕过
        sidecar_copy.write_text(json.dumps(faked, ensure_ascii=False, indent=2), encoding="utf-8")
        result = check_asset.evaluate(REPO, pack_path=pack_copy, sidecar_path=sidecar_copy)
        mutated_rows, _nodes, _groups = D.atomic_layout(REPO, pack_path=pack_copy)
        baseline_rows, _nodes, _groups = D.atomic_layout(REPO)
        return (result, D.layout_sha256(mutated_rows),
                [r["node_id"] for r in mutated_rows] == [r["node_id"] for r in baseline_rows])

    def assertOnlyLayoutShaFailed(self, result):
        failed = self._failed_names(result)
        self.assertEqual(failed, ["layoutSha256"],
                         "改包+重钉 packSha256 后必须**仅** `layoutSha256` 红（ids/行数都不变）；实际 %r" % failed)
        self.assertNotIn("layout", failed, "本负例的前提是 ids 序列不变，`layout` 不该红")
        self.assertNotIn("packSha256", failed, "旁车 packSha256 已重钉到改后包，它不该红")

    def test_canonicalNameChangeWithStableIdsIsRed(self):
        """N1：canonical name 改（`函数的概念` → `函数的概念（改）`），ids/行数不变 ⇒ 仅 `layoutSha256` 红。

        这消灭的是"ids 不变所以布局没变"的错觉：ids 只证明行属于哪些节点，不证明行的文本。
        """
        payload = json.loads(self.pack.read_text(encoding="utf-8"))
        self._first_non_empty_point(payload)["name"] = "函数的概念（改）"
        result, mutated_sha, ids_stable = self._evaluate_mutated_pack(payload, "n1.json")
        self.assertTrue(ids_stable, "N1 前提：ids 序列必须与基线完全相同")
        self.assertEqual(mutated_sha, "28fc9985374da6cbde5944d1c856a07e9e90d543cc2d92d8020e51850c4c9296",
                         "N1 的 layoutSha256 与预注册冻结值不符（跨语言序列化漂移或主包已变）")
        self.assertOnlyLayoutShaFailed(result)

    def test_equalCountAliasReplacementIsRed(self):
        """N2：等量替换别名（`aliases[0]` 换文本、条数仍 11），ids/行数不变 ⇒ 仅 `layoutSha256` 红。"""
        payload = json.loads(self.pack.read_text(encoding="utf-8"))
        point = self._first_non_empty_point(payload)
        alias_count = len(point["aliases"])
        point["aliases"][0] = "等量替换别名-函数的概念"
        self.assertEqual(len(point["aliases"]), alias_count, "N2 前提：别名条数必须不变")
        result, mutated_sha, ids_stable = self._evaluate_mutated_pack(payload, "n2.json")
        self.assertTrue(ids_stable, "N2 前提：ids 序列必须与基线完全相同")
        self.assertEqual(mutated_sha, "235faec73c011bc7fec8c3154a23b33763b0c6d9aee25463942fb49f8eea6494",
                         "N2 的 layoutSha256 与预注册冻结值不符（跨语言序列化漂移或主包已变）")
        self.assertOnlyLayoutShaFailed(result)

    def test_aliasOrderSwapIsRed(self):
        """N3：同一节点的 `aliases[0]` 与 `aliases[1]` 互换（集合/条数不变）⇒ 仅 `layoutSha256` 红。

        有序摘要必须抓住"顺序变了"，否则"行序 = 向量行序"的约定就没人守着。
        """
        payload = json.loads(self.pack.read_text(encoding="utf-8"))
        aliases = self._first_non_empty_point(payload)["aliases"]
        self.assertGreaterEqual(len(aliases), 2, "N3 前提：该点至少两条别名")
        aliases[0], aliases[1] = aliases[1], aliases[0]
        result, mutated_sha, ids_stable = self._evaluate_mutated_pack(payload, "n3.json")
        self.assertTrue(ids_stable, "N3 前提：ids 序列必须与基线完全相同")
        self.assertEqual(mutated_sha, "ca519f35e78b6c96a8c9dd9e9eae0845bb10e5e0e2dbd052b796510bbe774d3d",
                         "N3 的 layoutSha256 与预注册冻结值不符（有序摘要退化成了集合摘要？）")
        self.assertOnlyLayoutShaFailed(result)

    def test_vocabChangedIsRed(self):
        """反侧 2：词表被改过 ⇒ `vocab:*` 必须红。"""
        vocab_copy = self.tmp / "vocab.txt"
        vocab_copy.write_bytes(self.vocab.read_bytes() + b"\n[UNK]\n")
        result = check_asset.evaluate(REPO, vocab_path=vocab_copy)
        self.assertFalse(result["ok"], "词表变了，门必须红")
        self.assertIn("vocab:vocab", self._failed_names(result))

    def test_vectorCountMismatchInSidecarIsRed(self):
        """反侧 3：旁车自述的行数与 `.vec` 头不符 ⇒ `vectorCount` 必须红。"""
        sidecar_copy = self.tmp / "count.json"
        payload = json.loads(self.sidecar.read_text(encoding="utf-8"))
        payload["corpus"]["vectorCount"] = payload["corpus"]["vectorCount"] + 1
        sidecar_copy.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        result = check_asset.evaluate(REPO, sidecar_path=sidecar_copy)
        self.assertFalse(result["ok"], "旁车行数与向量头不符，门必须红")
        self.assertIn("vectorCount", self._failed_names(result))

    def test_missingSidecarIsRed(self):
        """反侧 4：缺旁车（资产没被生成/没入库）⇒ 红，且不抛异常。"""
        result = check_asset.evaluate(REPO, sidecar_path=self.tmp / "nope.json")
        self.assertFalse(result["ok"])
        self.assertIn("sidecar", self._failed_names(result))


if __name__ == "__main__":
    unittest.main()
