package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DetailedAnswerExportWiringTest {
    @Test fun answerWorkerRoundTripsItsExactApprovalAndFormat() {
        val request = MistakeExportJobRequest.DetailedAnswers("export", "group", "a".repeat(64), "b".repeat(64), MistakeExportFormat.DOCX,
            MistakePdfLayout.DEFAULT.copy(fontScale = 3))
        assertEquals(request, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(request)))
        assertNull(MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(request) - MistakeExportJobCodec.SOLUTIONS_SHA_KEY))
    }

    @Test fun groupedQuestionsKeepTheirManifestWhileLegacyEditedJobsStayCompatible() {
        val legacy = MistakeExportJobRequest.Edited("export", "paper")
        val grouped = legacy.copy(groupId = "group", manifestSha256 = "a".repeat(64))
        assertFalse(MistakeExportJobCodec.GROUP_ID_KEY in MistakeExportJobCodec.encode(legacy))
        assertEquals(legacy, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(legacy)))
        assertEquals(grouped, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(grouped)))
    }

    @Test fun changedAnswerOrFormatNeedsANewPreviewApproval() {
        val approval = AnswerPreviewApproval("a".repeat(64), "b".repeat(64), MistakeExportFormat.PDF)
        assertTrue(approvalMatches(approval, approval.manifestSha256, approval.solutionsSha256, MistakeExportFormat.PDF))
        assertFalse(approvalMatches(approval, approval.manifestSha256, "c".repeat(64), MistakeExportFormat.PDF))
        assertFalse(approvalMatches(approval, approval.manifestSha256, approval.solutionsSha256, MistakeExportFormat.DOCX))
    }
}
