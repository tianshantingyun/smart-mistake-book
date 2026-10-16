# -*- coding: utf-8 -*-
"""CI 状态页口径（AUD-15，2026-10-15 登记）。

它消灭的失败（都是"报表比实现更乐观"）：

1. `:app` 之外没有 lint 行——`lint<Flavor>Debug` 只匹配有 product flavor 的 :app，库模块的
   NewApi 在页面上一行都不出现，于是"没检查过"看起来和"检查过且通过"一样；
2. 只要 `build/` 里还留着 `.class`/`.jar` 就报编译 OK——那是**上一版源码**的产物；
3. `JOB_STATUS` 缺省 `"success"`——本地跑（没有这个环境变量）也能把 Overall 抬成 PASS；
4. `COMMIT_SHA`/`BRANCH` 写死 `local`/`unknown`——报表不携带"对应哪个提交"的信息，
   而门全绿这件事本身从来就不携带它。
5. 绑定成立 ≠ 描述对应（KD-40，2026-10-16 登记）：本地生成（或 CI commit-back 之前生成）时
   `BOUND · sha <生成时刻的 HEAD>` 指的是**上一版**提交，而页面只印 sha、不解释——读者会把 sha
   读成"这段描述对应的提交"。所以 `RUN_CONTEXT_NOTE` 必须和 sha/branch 一起进页面头部，
   且不随绑定结果变化。

这里的断言全部走**三值**口径：测到且绿 = True，测到且红 = False，没测到 = None；
Overall 只在全绿且全部测到时才是 PASS。
"""

from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from ci import generate_status as status

# KD-40 的那句话钉成字面量：它的唯一职责就是"sha 是生成时刻的绑定，不是本页所描述代码的提交"。
# 改文案必须同时改测试——否则页面会悄悄退回"只有 sha、不解释"的读法。
BINDING_NOTE = "绑定时刻 = 生成时刻，非所描述代码的提交"


def touch(path: Path, stamp: float) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("x", encoding="utf-8")
    os.utime(path, (stamp, stamp))


LINT_XML_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<issues format="6">
  <issue id="NewApi" severity="{severity}" message="m">
    <location file="X.kt" line="1" />
  </issue>
</issues>
"""

LINT_XML_CLEAN = """<?xml version="1.0" encoding="utf-8"?>
<issues format="6">
  <issue id="UnusedResources" severity="Warning" message="m">
    <location file="X.kt" line="1" />
  </issue>
</issues>
"""


class FakeRepoTest(unittest.TestCase):
    """把 REPO 指到临时目录，指到哪就长到哪——不用真构建。"""

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        patcher = mock.patch.object(status, "REPO", self.repo)
        patcher.start()
        self.addCleanup(patcher.stop)

    def module_sources(self, module: str, stamp: float) -> None:
        touch(status.gradle_dir(module) / "src" / "main" / "A.kt", stamp)

    def app_lint_report(self, variant: str, stamp: float, clean: bool = True) -> None:
        path = status.gradle_dir(":app") / "build" / "reports" / f"lint-results-{variant}.xml"
        touch(path, stamp)
        path.write_text(LINT_XML_CLEAN if clean else LINT_XML_TEMPLATE.format(severity="Error"), encoding="utf-8")
        os.utime(path, (stamp, stamp))

    def library_lint_report(self, module: str, stamp: float, *, clean: bool = True) -> None:
        path = status.gradle_dir(module) / "build" / "reports" / "lint-results-debug.xml"
        touch(path, stamp)
        path.write_text(LINT_XML_CLEAN if clean else LINT_XML_TEMPLATE.format(severity="Error"), encoding="utf-8")
        os.utime(path, (stamp, stamp))

    def all_lint_reports_clean(self) -> None:
        self.module_sources(":app", 100.0)
        for variant in ("localFirstDebug", "strictOfflineDebug"):
            self.app_lint_report(variant, 200.0)
        for module in status.LINT_LIBRARY_MODULES:
            self.module_sources(module, 100.0)
            self.library_lint_report(module, 200.0)


class LintSummaryTest(FakeRepoTest):
    def test_every_library_module_gets_its_own_row_and_clean_reports_pass(self) -> None:
        self.all_lint_reports_clean()

        values, ok, notes = status.lint_summary()

        self.assertIs(True, ok)
        self.assertEqual([], notes)
        self.assertEqual("OK", values["LINT_LIBRARY_STATUS"])
        for module in status.LINT_LIBRARY_MODULES:
            self.assertIn(f"| `{module}` |", values["LINT_LIBRARY_ROWS"])
        self.assertEqual("OK", values["LINT_LOCAL_FIRST_STATUS"])

    def test_missing_library_report_is_not_measured_not_pass(self) -> None:
        self.all_lint_reports_clean()
        missing = status.LINT_LIBRARY_MODULES[0]
        (status.gradle_dir(missing) / "build" / "reports" / "lint-results-debug.xml").unlink()

        values, ok, notes = status.lint_summary()

        self.assertIsNone(ok, "少一份库报告不能算通过")
        self.assertEqual(status.NOT_MEASURED, values["LINT_LIBRARY_STATUS"])
        self.assertIn(missing, notes[0])

    def test_error_in_a_library_report_is_fail(self) -> None:
        self.all_lint_reports_clean()
        bad = status.LINT_LIBRARY_MODULES[-1]
        self.library_lint_report(bad, 200.0, clean=False)

        values, ok, notes = status.lint_summary()

        self.assertIs(False, ok)
        self.assertEqual([], notes)
        self.assertEqual("FAIL", values["LINT_LIBRARY_STATUS"])
        self.assertEqual("1", values["LINT_LIBRARY_ERRORS"])

    def test_report_older_than_sources_is_not_evidence(self) -> None:
        self.all_lint_reports_clean()
        stale = status.LINT_LIBRARY_MODULES[1]
        self.module_sources(stale, 300.0)  # 报告之后又改了源码

        values, ok, notes = status.lint_summary()

        self.assertIsNone(ok, "上一版源码的 lint 报告不能报成本次 OK")
        self.assertIn("早于源码", notes[0])


class CompileEvidenceTest(FakeRepoTest):
    def test_artifact_newer_than_sources_is_ok(self) -> None:
        self.module_sources(":core:data", 100.0)
        touch(
            status.gradle_dir(":core:data")
            / "build"
            / "intermediates"
            / "compile_library_classes_jar"
            / "debug"
            / "classes.jar",
            200.0,
        )

        self.assertEqual(("OK", ""), status.compile_status(":core:data"))

    def test_leftover_artifact_from_previous_sources_is_not_measured(self) -> None:
        touch(
            status.gradle_dir(":core:data")
            / "build"
            / "intermediates"
            / "compile_library_classes_jar"
            / "debug"
            / "classes.jar",
            200.0,
        )
        self.module_sources(":core:data", 300.0)

        result, reason = status.compile_status(":core:data")

        self.assertEqual(status.NOT_MEASURED, result)
        self.assertIn("早于源码", reason)

    def test_no_artifact_is_not_measured(self) -> None:
        self.module_sources(":core:data", 100.0)

        self.assertEqual((status.NOT_MEASURED, "无编译产物"), status.compile_status(":core:data"))

    def test_loose_classes_under_build_classes_are_accepted_when_fresh(self) -> None:
        self.module_sources(":core:domain", 100.0)
        touch(status.gradle_dir(":core:domain") / "build" / "classes" / "kotlin" / "main" / "A.class", 200.0)

        self.assertEqual(("OK", ""), status.compile_status(":core:domain"))


class RunContextTest(unittest.TestCase):
    def test_git_head_is_used_when_ci_variables_are_absent(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(
            status, "git_output", side_effect=lambda *args: "a" * 40 if args == ("rev-parse", "HEAD") else "main"
        ):
            values, bound = status.run_context()

        self.assertTrue(bound)
        self.assertEqual("a" * 40, values["COMMIT_SHA"])
        self.assertEqual("main", values["BRANCH"])
        self.assertTrue(values["RUN_CONTEXT"].startswith("BOUND"), values["RUN_CONTEXT"])
        self.assertEqual(BINDING_NOTE, values["RUN_CONTEXT_NOTE"], "绑定成立时也要自述 sha 是生成时刻的绑定")

    def test_unavailable_head_is_unknown_and_unbound(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(
            status, "git_output", return_value=None
        ):
            values, bound = status.run_context()

        self.assertFalse(bound, "拿不到 HEAD 时上下文不绑定")
        self.assertEqual("unknown", values["COMMIT_SHA"])
        self.assertEqual("unknown", values["BRANCH"])
        self.assertIn("UNBOUND", values["RUN_CONTEXT"])
        self.assertNotIn("local", values["COMMIT_SHA"])
        self.assertEqual(BINDING_NOTE, values["RUN_CONTEXT_NOTE"], "未绑定时头部照样有 sha/branch 行，说明不能缺席")

    def test_declared_sha_that_disagrees_with_head_is_unbound(self) -> None:
        env = {"COMMIT_SHA": "b" * 40, "BRANCH": "main"}
        with mock.patch.dict(os.environ, env, clear=True), mock.patch.object(
            status, "git_output", side_effect=lambda *args: "a" * 40 if args == ("rev-parse", "HEAD") else "main"
        ):
            values, bound = status.run_context()

        self.assertFalse(bound)
        self.assertIn("不一致", values["RUN_CONTEXT"])
        self.assertEqual("b" * 40, values["COMMIT_SHA"])
        self.assertEqual(BINDING_NOTE, values["RUN_CONTEXT_NOTE"], "印的是 declared sha（≠HEAD）时更要说清它是什么")

    def test_declared_sha_matching_head_is_bound(self) -> None:
        env = {"COMMIT_SHA": "a" * 40, "BRANCH": "main", "TRIGGERED_BY": "push"}
        with mock.patch.dict(os.environ, env, clear=True), mock.patch.object(
            status, "git_output", side_effect=lambda *args: "a" * 40 if args == ("rev-parse", "HEAD") else "main"
        ):
            values, bound = status.run_context()

        self.assertTrue(bound)
        self.assertIn("与 git HEAD 一致", values["RUN_CONTEXT"])
        self.assertEqual("push", values["TRIGGERED_BY"])


class RunContextNoteTemplateTest(unittest.TestCase):
    """KD-40 的另一半：那句话必须真的随模板进页面（这里读的是**仓库里那一份**模板）。

    只改 `run_context` 不改模板、或占位符拼错，页面不会报错——替换扫描（main 尾部）会把
    `{{RUN_CONTEXT_NOTE}}` 兜成 NOT_MEASURED，于是"没印说明"长得像"没测到说明"。
    """

    def template_text(self) -> str:
        return status.TEMPLATE.read_text(encoding="utf-8")

    def test_note_placeholder_sits_directly_under_the_run_context_line(self) -> None:
        lines = self.template_text().splitlines()
        context_line = next((i for i, line in enumerate(lines) if "{{RUN_CONTEXT}}" in line), None)
        note_line = next((i for i, line in enumerate(lines) if "{{RUN_CONTEXT_NOTE}}" in line), None)

        self.assertIsNotNone(context_line, "模板里没有 {{RUN_CONTEXT}}")
        self.assertIsNotNone(note_line, "模板缺 {{RUN_CONTEXT_NOTE}}：页面不会印 KD-40 的那句话")
        self.assertEqual(context_line + 1, note_line, "绑定说明要贴在 Run context 下面，不能散落别处")

    def test_note_survives_rendering(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(
            status, "git_output", side_effect=lambda *args: "a" * 40 if args == ("rev-parse", "HEAD") else "main"
        ):
            values, _ = status.run_context()

        rendered = self.template_text()
        for key, value in values.items():
            rendered = rendered.replace("{{" + key + "}}", value)

        self.assertNotIn("{{RUN_CONTEXT_NOTE}}", rendered, "占位符没被替换 ⇒ 页面印的是 NOT_MEASURED")
        self.assertIn(BINDING_NOTE, rendered, "页面头部要能看到这句话本身")


class JobAndOverallTest(unittest.TestCase):
    def test_job_flag_has_no_default_success(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(status.job_flag(), "缺 JOB_STATUS 是没测，不是 success")
        with mock.patch.dict(os.environ, {"JOB_STATUS": "success"}, clear=True):
            self.assertIs(status.job_flag(), True)
        with mock.patch.dict(os.environ, {"JOB_STATUS": "failure"}, clear=True):
            self.assertIs(status.job_flag(), False)

    def test_overall_is_three_valued(self) -> None:
        self.assertEqual("PASS", status.overall_status([True] * 6))
        self.assertEqual(
            status.NOT_MEASURED,
            status.overall_status([None, True, True, True, True, True]),
            "没测到的 build job（缺 JOB_STATUS）不得抬成 PASS",
        )
        self.assertEqual(
            status.NOT_MEASURED,
            status.overall_status([True, True, True, None, True, True]),
            "缺 lint/编译证据不得抬成 PASS",
        )
        self.assertEqual(
            "FAIL",
            status.overall_status([True, True, True, True, True, False]),
            "run 上下文未绑定 ⇒ Overall 不得 PASS",
        )
        self.assertEqual("FAIL", status.overall_status([True, False, True, True, True, True]))


if __name__ == "__main__":
    unittest.main()
