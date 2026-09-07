package com.example.vision

import kotlin.math.hypot

/** A missing observation clears the overlay immediately. No prediction is presented as detection. */
class StablePanelTracker {
    private var previous: PanelMatch?=null
    private var count=0
    private var timestamp=0L
    data class State(val candidate: PanelMatch?,val stable:Boolean,val consecutive:Int)
    fun reset(){previous=null;count=0;timestamp=0}
    fun update(match:PanelMatch?,nowMs:Long):State {
        if(match==null){reset();return State(null,false,0)}
        val old=previous
        val close=old!=null && old.face==match.face && nowMs-timestamp in 0..1500 && old.corners.zip(match.corners).all { (a,b) -> hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble())<110 }
        count=if(close)count+1 else 1
        previous=match;timestamp=nowMs
        return State(match,count>=3,count)
    }
}
