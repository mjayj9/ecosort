package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.*

data class ImagePoint(val x: Float, val y: Float)
data class PanelMatch(val face: String, val label: String, val corners: List<ImagePoint>, val points: List<ImagePoint>, val inliers: Int, val matches: Int)
data class Detection(val match: PanelMatch?, val elapsedMs: Long)
data class Panel(val id: String, val label: String, val bitmap: Bitmap)

/** Receives only pixels. Renderer face IDs, product state and expected corners are not inputs. */
class CartonDetector(context: Context) : AutoCloseable {
    private val orb: ORB
    private val matcher: DescriptorMatcher
    private data class Reference(val panel: Panel, val keys: Array<KeyPoint>, val descriptor: Mat)
    val panels: List<Panel>
    private val references: List<Reference>
    init {
        check(OpenCVLoader.initLocal()) { "기기의 영상 처리 모듈을 시작하지 못했습니다." }
        Core.setNumThreads(1)
        orb = ORB.create(1600)
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        panels = listOf("front" to "앞면", "back" to "뒷면·부착 빨대", "ingredients" to "원재료 면", "nutrition" to "분리배출 표시 면", "top" to "윗면", "bottom" to "아랫면").map { (id,label) ->
            Panel(id,label,context.assets.open("carton/$id.jpg").use { BitmapFactory.decodeStream(it) })
        }
        references = panels.map { panel ->
            val gray = gray(panel.bitmap); val keys = MatOfKeyPoint(); val descriptor = Mat(); val mask = Mat()
            try { orb.detectAndCompute(gray,mask,keys,descriptor); Reference(panel,keys.toArray(),descriptor) }
            finally { gray.release(); keys.release(); mask.release() }
        }
    }
    private fun gray(bitmap: Bitmap): Mat {
        val rgba=Mat();val gray=Mat()
        try { Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);return gray }
        finally { rgba.release() }
    }
    @Synchronized fun detect(bitmap: Bitmap): Detection {
        val start=SystemClock.elapsedRealtime(); val gray=gray(bitmap);val keys=MatOfKeyPoint();val descriptor=Mat();val mask=Mat()
        try {
            orb.detectAndCompute(gray,mask,keys,descriptor)
            if(descriptor.empty())return Detection(null,SystemClock.elapsedRealtime()-start)
            val target=keys.toArray()
            val candidates=references.mapNotNull { reference -> match(reference,target,descriptor,bitmap.width,bitmap.height) }
            val best=candidates.maxByOrNull { it.inliers }
            return Detection(best,SystemClock.elapsedRealtime()-start)
        } finally { gray.release();keys.release();descriptor.release();mask.release() }
    }
    private fun match(ref: Reference, target: Array<KeyPoint>, descriptor: Mat, width: Int, height: Int): PanelMatch? {
        val pairs=ArrayList<MatOfDMatch>()
        try {
            matcher.knnMatch(ref.descriptor,descriptor,pairs,2)
            val good=pairs.mapNotNull { p -> val m=p.toArray();m.firstOrNull()?.takeIf { m.size==2 && it.distance < .74f*m[1].distance } }
                .sortedBy { it.distance }.distinctBy { it.trainIdx }
            if(good.size<18)return null
            val source=MatOfPoint2f(*good.map { ref.keys[it.queryIdx].pt }.toTypedArray())
            val destination=MatOfPoint2f(*good.map { target[it.trainIdx].pt }.toTypedArray())
            val inlierMask=Mat();val inputCorners=MatOfPoint2f(Point(0.0,0.0),Point(ref.panel.bitmap.width.toDouble()-1,0.0),Point(ref.panel.bitmap.width.toDouble()-1,ref.panel.bitmap.height.toDouble()-1),Point(0.0,ref.panel.bitmap.height.toDouble()-1));val projected=MatOfPoint2f()
            var homography: Mat?=null
            try {
                homography=Calib3d.findHomography(source,destination,Calib3d.RANSAC,3.5,inlierMask,2000,.995)
                if(homography.empty())return null
                val accepted=good.indices.filter { inlierMask.get(it,0)?.firstOrNull()==1.0 }
                if(accepted.size<14 || accepted.size.toDouble()/good.size<.55)return null
                val observed=accepted.map { ref.keys[good[it].queryIdx].pt }
                if((observed.maxOf{it.x}-observed.minOf{it.x})/ref.panel.bitmap.width<.25 || (observed.maxOf{it.y}-observed.minOf{it.y})/ref.panel.bitmap.height<.25)return null
                val cells=observed.map { min(2,(it.x/ref.panel.bitmap.width*3).toInt()) to min(2,(it.y/ref.panel.bitmap.height*3).toInt()) }.toSet()
                if(cells.size<4)return null
                Core.perspectiveTransform(inputCorners,projected,homography)
                val quad=projected.toArray()
                if(!validQuad(quad,width,height))return null
                return PanelMatch(ref.panel.id,ref.panel.label,quad.map { ImagePoint(it.x.toFloat(),it.y.toFloat()) },accepted.take(80).map { val p=target[good[it].trainIdx].pt;ImagePoint(p.x.toFloat(),p.y.toFloat()) },accepted.size,good.size)
            } finally { source.release();destination.release();inlierMask.release();inputCorners.release();projected.release();homography?.release() }
        } finally { pairs.forEach { it.release() } }
    }
    private fun validQuad(p: Array<Point>,w:Int,h:Int):Boolean {
        if(p.size!=4 || p.any { !it.x.isFinite() || !it.y.isFinite() || it.x<-.2*w || it.y<-.2*h || it.x>1.2*w || it.y>1.2*h })return false
        val cross=p.indices.map { i ->val a=p[i];val b=p[(i+1)%4];val c=p[(i+2)%4];(b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x) }
        if(cross.any{it<=0})return false
        val area=abs(p.indices.sumOf { i ->p[i].x*p[(i+1)%4].y-p[(i+1)%4].x*p[i].y })/2
        return area>w*h*.012 && area<w*h*.95 && p.indices.all { i -> hypot(p[i].x-p[(i+1)%4].x,p[i].y-p[(i+1)%4].y)>22 }
    }
    @Synchronized override fun close() { references.forEach { it.descriptor.release() };orb.clear();matcher.clear() }
}
