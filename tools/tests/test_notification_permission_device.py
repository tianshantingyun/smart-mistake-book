"""An instrumentation process exit code alone cannot establish a passing test."""

import pytest

from ci.check_notification_permission_device import instrumentation_passed


def test_one_completed_denial_test_passes():
    assert instrumentation_passed(0, "Time: 0.4\n\nOK (1 test)\n")


@pytest.mark.parametrize("output", [
    "", "OK (0 tests)\n", "OK (2 tests)\n", "OK (1 test)\nFAILURES!!!",
    "OK (1 test)\nINSTRUMENTATION_FAILED", "OK (1 test)\nProcess crashed",
    "OK (1 test)\nINSTRUMENTATION_ABORTED", "prefix OK (1 test)\n",
])
def test_incomplete_or_mixed_failure_output_is_rejected(output):
    assert not instrumentation_passed(0, output)


def test_failed_process_is_rejected_even_with_a_pass_line():
    assert not instrumentation_passed(1, "OK (1 test)\n")
