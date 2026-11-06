package com.tingyun.smartmistakebook.feature.review

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaperReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun draftsRemainSeparateAndRestoreWithoutChangingSavedResults() {
        val handle = SavedStateHandle()
        val vm = PaperReviewViewModel(FakePaperRepository(), handle)
        vm.edit("paper", question) { it.copy(note = "原答案为 B", result = ReviewSelfReportKey.AGAIN) }
        vm.edit("paper", question.copy(ordinal = 2)) { it.copy(note = "另一题") }
        val restored = PaperReviewViewModel(FakePaperRepository(), handle)
        assertEquals("原答案为 B", restored.draft("paper", question).note)
        assertEquals("另一题", restored.draft("paper", question.copy(ordinal = 2)).note)
        assertTrue(restored.hasUnsaved("paper", listOf(question)))
        assertNull(question.selectedResult)
    }

    @Test fun duplicateSaveIsBlockedBeforeCoroutineStarts() = runTest(dispatcher) {
        val repo = FakePaperRepository()
        val vm = PaperReviewViewModel(repo)
        vm.edit("paper", question) { it.copy(result = ReviewSelfReportKey.AGAIN, answeredAt = 100L) }
        vm.confirm("paper", question)
        vm.confirm("paper", question)
        assertTrue(vm.busy.value)
        advanceUntilIdle()
        assertEquals(1, repo.confirmed.size)
        assertEquals(1, repo.applied)
        assertFalse(vm.busy.value)
        assertFalse(vm.hasUnsaved("paper", listOf(question)))
    }

    @Test fun failurePreservesDraftAndShowsQuestionLocalError() = runTest(dispatcher) {
        val repo = FakePaperRepository().apply { fail = true }
        val vm = PaperReviewViewModel(repo)
        vm.edit("paper", question) { it.copy(note = "保留说明", answeredAt = 100L) }
        vm.confirm("paper", question)
        advanceUntilIdle()
        assertTrue(vm.feedback.value!!.error)
        assertEquals(1, vm.feedback.value!!.ordinal)
        assertEquals("保留说明", vm.draft("paper", question).note)
        assertTrue(vm.hasUnsaved("paper", listOf(question)))
        assertEquals(0, repo.applied)
    }

    @Test fun discardRemovesOnlyCurrentPaperDrafts() {
        val vm = PaperReviewViewModel(FakePaperRepository())
        vm.edit("one", question) { it.copy(note = "一") }
        vm.edit("two", question) { it.copy(note = "二") }
        vm.discard("one")
        assertEquals("", vm.draft("one", question).note)
        assertEquals("二", vm.draft("two", question).note)
    }

    private val question = PaperReviewQuestion(1, "entry", "unit", "problem", "revision", "题目", "原题")
    private class FakePaperRepository : PaperReviewRepository {
        val confirmed = mutableListOf<PaperReviewConfirmation>()
        var applied = 0
        var fail = false
        override fun observe() = flowOf(emptyList<PaperReview>())
        override fun observe(paperId: String) = flowOf<PaperReview?>(null)
        override suspend fun create(review: PaperReview) = review.paperId
        override suspend fun export(paperId: String) = "export"
        override suspend fun addPhotos(paperId: String, localUris: List<String>, corrected: Boolean, questionOrdinal: Int?) = Unit
        override suspend fun confirm(command: PaperReviewConfirmation) {
            if (fail) error("保存失败，请重试")
            confirmed += command
        }
        override suspend fun applyConfirmed(paperId: String): String { applied++; return "已计入复习记录" }
        override suspend fun draftSnapshot(paperId: String) = "{}"
    }
}
