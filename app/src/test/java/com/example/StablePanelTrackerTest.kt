package com.example

import com.example.vision.*
import org.junit.Assert.*
import org.junit.Test

class StablePanelTrackerTest {
    private fun panel(face:String="front",offset:Float=0f)=PanelMatch(face,face,listOf(ImagePoint(offset,0f),ImagePoint(100f+offset,0f),ImagePoint(100f+offset,200f),ImagePoint(offset,200f)),emptyList(),30,40)
    @Test fun threeConsistentObservationsRequired(){val t=StablePanelTracker();assertFalse(t.update(panel(),0).stable);assertFalse(t.update(panel(),100).stable);assertTrue(t.update(panel(),200).stable)}
    @Test fun lostTargetClearsImmediately(){val t=StablePanelTracker();repeat(3){t.update(panel(),it*100L)};assertNull(t.update(null,400).candidate);assertFalse(t.update(panel(),500).stable)}
    @Test fun faceSwitchResetsEvidence(){val t=StablePanelTracker();repeat(3){t.update(panel(),it*100L)};assertEquals(1,t.update(panel("top"),300).consecutive)}
    @Test fun timeGapAndJumpDoNotReuseOldEvidence(){val t=StablePanelTracker();repeat(3){t.update(panel(),it*100L)};assertFalse(t.update(panel(),2000).stable);assertEquals(1,t.update(panel(offset=200f),2100).consecutive)}
}
