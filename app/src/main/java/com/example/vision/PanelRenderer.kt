package com.example.vision

import android.graphics.*
import kotlin.math.*

data class PanelPose(val angle: Float=0f,val tilt: Float=0f,val scale: Float=1f,val offset: Float=0f,val obstruction: Boolean=false,val blank: Boolean=false)

/** Synthetic INPUT generator only. No detector result is derived from its coordinates. */
object PanelRenderer {
    const val WIDTH=640
    const val HEIGHT=480
    fun render(panel: Bitmap,pose:PanelPose):Bitmap {
        val out=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(out);canvas.drawColor(Color.rgb(217,223,219))
        if(pose.blank)return out
        val h=370f*pose.scale;val w=h*panel.width/panel.height
        val fit=min(1f,470f/w);val rw=w*fit;val rh=h*fit
        val a=pose.angle/180f*PI.toFloat();val cx=320f+pose.offset*120f;val cy=240f
        val local=floatArrayOf(-rw/2,-rh/2,rw/2,-rh/2,rw/2,rh/2,-rw/2,rh/2)
        val dest=FloatArray(8)
        for(i in 0..3){val x=local[2*i];val y=local[2*i+1];val perspective=1f+pose.tilt*x/max(rw,1f)*.7f;val px=x/perspective;val py=y/perspective;dest[2*i]=cx+px*cos(a)-py*sin(a);dest[2*i+1]=cy+px*sin(a)+py*cos(a)}
        val source=floatArrayOf(0f,0f,panel.width.toFloat(),0f,panel.width.toFloat(),panel.height.toFloat(),0f,panel.height.toFloat())
        val m=Matrix();check(m.setPolyToPoly(source,0,dest,0,4))
        canvas.drawBitmap(panel,m,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if(pose.obstruction)canvas.drawRect(cx-80,cy-70,cx+80,cy+70,Paint().apply { color=Color.rgb(80,86,84) })
        return out
    }
}
