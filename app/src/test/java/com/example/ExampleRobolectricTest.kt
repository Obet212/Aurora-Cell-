package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineOcrService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aurora Cell", appName)
  }

  @Test
  fun `decodeFullResolutionBitmap preserves original dimensions without compression or cropping`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "test_receipt.png")
    val bmp = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
    FileOutputStream(file).use { out ->
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bmp.recycle()

    val service = OfflineOcrService(context)
    val decoded = service.decodeFullResolutionBitmap(Uri.fromFile(file))
    assertEquals(120, decoded.width)
    assertEquals(200, decoded.height)
    decoded.recycle()
  }

  @Test
  fun `preprocessReceiptBitmap upscales 2x and binarizes watermarks above luminance 200`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val src = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(src)
    // Background watermark light blue (luminance ~229 > 200 -> must become WHITE)
    canvas.drawColor(Color.rgb(215, 232, 248))
    // Foreground dark text pixel (luminance ~50 <= 200 -> must become BLACK)
    src.setPixel(3, 4, Color.rgb(40, 50, 65))

    val service = OfflineOcrService(context)
    val processed = service.preprocessReceiptBitmap(src)

    assertEquals(20, processed.width)
    assertEquals(20, processed.height)
    assertEquals(Color.WHITE, processed.getPixel(0, 0))
    assertEquals(Color.BLACK, processed.getPixel(6, 8))

    src.recycle()
    processed.recycle()
  }
}
