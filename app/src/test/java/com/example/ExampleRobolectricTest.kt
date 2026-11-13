package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineOcrService
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
  fun `decodePreparedBitmap succeeds on valid image uri`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "test_receipt.png")
    val bmp = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
    FileOutputStream(file).use { out ->
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bmp.recycle()

    val service = OfflineOcrService(context)
    val prepared = service.decodePreparedBitmap(Uri.fromFile(file))
    assertTrue(prepared.width >= 120)
    assertTrue(prepared.height >= 200)
    prepared.recycle()
  }
}
