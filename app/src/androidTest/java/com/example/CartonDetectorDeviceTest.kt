package com.example

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vision.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CartonDetectorDeviceTest {
    @Test fun actualNativeDetectorSyntheticTransformsAndNegatives(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val rows=JSONArray()
        CartonDetector(context).use { detector ->
            for(panel in detector.panels)for(angle in listOf(-25f,0f,25f)){
                val frame=PanelRenderer.render(panel.bitmap,PanelPose(angle=angle,tilt=.25f,offset=angle/100,scale=.9f))
                val result=detector.detect(frame)
                rows.put(JSONObject().put("input",panel.id).put("angle",angle).put("detected",result.match?.face).put("ms",result.elapsedMs).put("inliers",result.match?.inliers?:0).put("kind","synthetic-reference-transform"))
                frame.recycle()
            }
            val blank=PanelRenderer.render(detector.panels.first().bitmap,PanelPose(blank=true))
            val empty=detector.detect(blank);assertNull("Blank must not match",empty.match);blank.recycle()
            val testContext=InstrumentationRegistry.getInstrumentation().context
            testContext.assets.open("negative-bottle.jpg").use { input ->
                val image=BitmapFactory.decodeStream(input);val result=detector.detect(image)
                rows.put(JSONObject().put("input","different-bottle").put("detected",result.match?.face).put("ms",result.elapsedMs).put("kind","negative-real-photo"));assertNull("Different product must not match",result.match);image.recycle()
            }
            for(i in 1554..1559){
                testContext.assets.open("IMG_$i.jpg").use { input ->
                    val image=BitmapFactory.decodeStream(input);val result=detector.detect(image)
                    rows.put(JSONObject().put("input","IMG_$i").put("detected",result.match?.face).put("ms",result.elapsedMs).put("inliers",result.match?.inliers?:0).put("kind","registration-original-not-independent"));image.recycle()
                }
            }
        }
        val report=JSONObject().put("independentAccuracyBenchmark",false).put("cameraUsed",false).put("rows",rows)
        File(context.filesDir,"phase2-detector-test.json").writeText(report.toString(2))
        val missed=(0 until 18).count { rows.getJSONObject(it).optString("input")!=rows.getJSONObject(it).optString("detected") }
        assertEquals("Every specified synthetic transform must recover the right registered panel",0,missed)
    }
}
