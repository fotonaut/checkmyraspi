package de.mchwrk.checkmyraspi.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/** Standortfotos: verkleinert (max. 1600 px) und gedreht in filesDir/photos abgelegt. */
class Photos(private val context: Context) {
    val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Temporäre Datei für die Kamera-App (über FileProvider freigegeben). */
    fun newCaptureFile(): File = File(context.cacheDir, "capture").apply { mkdirs() }
        .let { File(it, "cap-${UUID.randomUUID()}.jpg") }

    fun import(uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1600) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
        val name = "loc-${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    fun clearCaptures() {
        File(context.cacheDir, "capture").listFiles()?.forEach { it.delete() }
    }
}
