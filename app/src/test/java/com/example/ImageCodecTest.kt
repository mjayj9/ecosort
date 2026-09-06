package com.example
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.exifinterface.media.ExifInterface
import com.example.repository.ImageCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageCodecTest {
    @Test fun largeImageBecomesBoundedJpeg() {
        val bitmap = Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val encoded = ImageCodec.encode(bitmap)
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1024, result.width); assertEquals(512, result.height); assertTrue(bytes.size <= ImageCodec.MAX_BYTES)
        assertFalse(encoded.contains('\n')); assertFalse(bitmap.isRecycled)
    }
    @Test fun galleryRespectsExifOrientationAndStripsMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "rotated-test.jpg")
        file.outputStream().use { Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val bitmap = ImageCodec.read(context, Uri.fromFile(file))
        assertEquals(200, bitmap.width); assertEquals(400, bitmap.height)
        val clean = Base64.decode(ImageCodec.encode(bitmap), Base64.NO_WRAP)
        val exif = ExifInterface(clean.inputStream())
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNotEquals(ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
    }
}
