package de.bedrockchat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Verantwortlich für das Lesen, Skalieren und Base64-Kodieren von Bildern.
 * Isoliert die gesamte Bitmap-Logik vom ViewModel.
 */
class ImageProcessor(private val context: Context) {

    data class EncodedImage(val base64: String, val mimeType: String)

    fun encode(uri: Uri): EncodedImage? = runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(stream).also { stream.close() } ?: return null
        val bitmap = original.scaleToMaxDimension(MAX_DIMENSION)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        EncodedImage(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), MIME_JPEG)
    }.getOrNull()

    private fun Bitmap.scaleToMaxDimension(maxDim: Int): Bitmap {
        val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height, 1f)
        if (scale >= 1f) return this
        val scaled = Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        recycle()
        return scaled
    }

    companion object {
        private const val MAX_DIMENSION = 1024
        private const val JPEG_QUALITY  = 85
        private const val MIME_JPEG     = "jpeg"
    }
}
