package com.example

import com.example.repository.*
import com.example.ui.screens.questionApplies
import org.junit.Assert.*
import org.junit.Test

class QuestionApplicabilityTest {
    private fun question(key: String) = GuidanceQuestion(key, "테스트", listOf(GuidanceChoice("UNKNOWN", "모르겠음")))
    @Test fun changingToUnusedCannotLeaveOldResidueQuestionActive() {
        val old = ScanAnswers(ScanPurpose.DISPOSE_NOW, UseState.USED, ContentsState.EMPTY, ResidueState.NONE_VISIBLE)
        assertTrue(questionApplies(question("residue"), old))
        val changed = old.choose("useState", "UNUSED")
        assertFalse(questionApplies(question("residue"), changed))
        assertFalse(questionApplies(question("contents"), changed))
    }
    @Test fun changingToPreviewDoesNotAskAboutCurrentWaste() {
        assertFalse(questionApplies(question("contents"), ScanAnswers(ScanPurpose.PREVIEW, UseState.USED)))
        assertTrue(questionApplies(question("material"), ScanAnswers(ScanPurpose.PREVIEW, UseState.UNUSED)))
    }
}
