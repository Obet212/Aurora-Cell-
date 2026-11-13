package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiOcrService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
  fun `compressUriToJpegBase64 succeeds on valid image uri`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "test_receipt.png")
    val bmp = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
    FileOutputStream(file).use { out ->
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bmp.recycle()

    val service = GeminiOcrService(context)
    val base64 = service.compressUriToJpegBase64(Uri.fromFile(file), maxWidth = 1024)
    assertTrue(base64.isNotBlank())
  }
}
