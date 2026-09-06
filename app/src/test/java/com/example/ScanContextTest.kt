package com.example

import com.example.repository.*
import org.junit.Assert.*
import org.junit.Test

class ScanContextTest {
    @Test fun unansweredAndUnknownRemainDifferent() {
        val blank = ScanAnswers()
        assertFalse(blank.readyForAnalysis)
        assertTrue(blank.toMap().isEmpty())
        val unknown = blank.choose("purpose", "UNKNOWN").choose("useState", "UNKNOWN")
        assertTrue(unknown.readyForAnalysis)
        assertEquals("UNKNOWN", unknown.toMap()["useState"])
        assertFalse(unknown.toMap().containsKey("contents"))
    }
    @Test fun changingToUnusedDiscardsPreviousEmptyAndCleanAnswers() {
        val used = ScanAnswers(ScanPurpose.DISPOSE_NOW, UseState.USED, ContentsState.EMPTY, ResidueState.NONE_VISIBLE)
        val unused = used.choose("useState", "UNUSED")
        assertNull(unused.contents)
        assertNull(unused.residue)
    }
    @Test fun newPhotoCannotInheritOldProductOrLateResponse() {
        val old = ScanDraft().choose("purpose", "PREVIEW").choose("useState", "UNUSED")
        val next = old.newPhoto()
        assertFalse(next.accepts(old.ticket))
        assertTrue(next.answers.toMap().isEmpty())
    }
    @Test fun lateResponseForEarlierAnswersIsRejected() {
        val draft = ScanDraft().choose("purpose", "DISPOSE_NOW")
        val changed = draft.choose("purpose", "PREVIEW")
        assertFalse(changed.accepts(draft.ticket))
        assertTrue(changed.accepts(changed.ticket))
    }
    @Test(expected = IllegalStateException::class) fun arbitraryServerFactsCannotBeSubmittedAsAnswers() {
        ScanAnswers().choose("decision", "RECYCLE")
    }
}
