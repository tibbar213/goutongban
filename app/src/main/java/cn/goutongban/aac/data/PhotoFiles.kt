package cn.goutongban.aac.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

class PhotoFiles(private val context: Context) {
    private val folder = File(context.filesDir, "photos")

    fun import(uri: Uri): String {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = max(info.size.width, info.size.height)
                if (largest > MaxSide) {
                    decoder.setTargetSize(
                        (info.size.width.toLong() * MaxSide / largest).toInt().coerceAtLeast(1),
                        (info.size.height.toLong() * MaxSide / largest).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("无法读取图片")
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (max(options.outWidth, options.outHeight) / options.inSampleSize > MaxSide) {
                options.inSampleSize *= 2
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("无法读取图片")
        }
        val name = UUID.randomUUID().toString() + ".jpg"
        val target = File(folder, name)
        try {
            folder.mkdirs()
            target.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) throw IOException("无法保存图片")
            }
        } catch (error: IOException) {
            target.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
        return name
    }

    fun resolve(name: String): File = File(folder, name)

    fun delete(name: String?) {
        if (name != null) resolve(name).delete()
    }

    private companion object {
        const val MaxSide = 1024
    }
}
