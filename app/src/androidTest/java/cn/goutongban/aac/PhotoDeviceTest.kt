package cn.goutongban.aac

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.goutongban.aac.data.PhotoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoDeviceTest {
    @Test fun photoIsCopiedResizedAndCanBeDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("board-photo", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1400, 700, Bitmap.Config.ARGB_8888)
        val photos = PhotoFiles(context)
        var name: String? = null
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val imported = photos.import(Uri.fromFile(source))
            name = imported
            assertTrue(source.delete())
            val local = photos.resolve(imported)
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(local.path, size)
            assertTrue(size.outWidth in 1..1024)
            assertEquals(size.outWidth / 2, size.outHeight)
            photos.delete(imported)
            assertFalse(local.exists())
        } finally {
            bitmap.recycle()
            photos.delete(name)
            source.delete()
        }
    }
}
