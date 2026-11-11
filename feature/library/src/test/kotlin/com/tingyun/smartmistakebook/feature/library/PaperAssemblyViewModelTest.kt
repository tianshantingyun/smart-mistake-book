package com.tingyun.smartmistakebook.feature.library

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test
import com.tingyun.smartmistakebook.core.domain.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class PaperAssemblyViewModelTest {
    @Test fun changedOrMissingQuestionsRequireAnExplicitDecision() = runTest {
        val state = SavedStateHandle()
        val model = PaperAssemblyViewModel(state)
        model.toggle("a", "old")
        assertEquals("old", PaperAssemblyViewModel(state).expectedRevisions["a"])
        val detail = MistakeDetail(
            MistakeDetailIdentity("a", "problem", "new", 2, "题目", "MATH"),
            "题干", MistakeSourceSet.Missing,
        )
        val repository = object : MistakeDetailRepository {
            override fun observe(errorBookEntryId: String) = flowOf<MistakeDetailState>(MistakeDetailState.Legacy(detail))
            override fun observeExact(key: MistakeRevisionKey) = observe(key.entryId)
            override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState = MistakeDetailState.Legacy(detail)
        }
        assertFalse(model.validateForEditing(repository))
        assertTrue(model.notice.orEmpty().contains("已更新"))
        model.acceptCurrentVersion("a", "new")
        assertTrue(model.validateForEditing(repository))
        model.toggle("a")
        assertTrue(model.expectedRevisions.isEmpty())
        assertFalse(model.checking)
    }
    @Test fun selectionOrderSurvivesReconstruction() {
        val state = SavedStateHandle()
        val model = PaperAssemblyViewModel(state)
        model.toggle("a")
        model.toggle("b")
        model.move("b", -1)
        assertEquals(listOf("b", "a"), model.selectedEntryIds)
        assertEquals(model.selectedEntryIds, PaperAssemblyViewModel(state).selectedEntryIds)
        model.toggle("b")
        assertEquals(listOf("a"), model.selectedEntryIds)
    }

    @Test fun boundsAndLimitPreserveSelection() {
        val model = PaperAssemblyViewModel(SavedStateHandle())
        repeat(100) { model.toggle("entry-$it") }
        model.toggle("extra")
        model.move("entry-0", -1)
        model.move("missing", 1)
        assertEquals(100, model.selectedEntryIds.size)
        assertEquals("entry-0", model.selectedEntryIds.first())
        assertNotNull(model.notice)
        model.toggle("entry-0")
        model.toggle("extra")
        assertEquals("extra", model.selectedEntryIds.last())
    }
}
