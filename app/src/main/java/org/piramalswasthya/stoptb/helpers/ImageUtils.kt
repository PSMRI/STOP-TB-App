package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.text.isDigitsOnly
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object ImageUtils {

    /** Hard cap for registration photos sent to and stored from the API. */
    const val MAX_REGISTRATION_IMAGE_BYTES = 400 * 1024

    suspend fun saveBenImageFromCameraToStorage(
        context: Context,
        uriString: String,
        benId: Long
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val targetFile = File(context.filesDir, "${benId}.jpeg")
                val compressed = compressToJpeg(
                    context,
                    Uri.parse(uriString),
                    targetFile,
                    label = "benId=$benId camera"
                )
                if (!compressed) {
                    Timber.e("ImageUtils: Failed to compress camera image for benId=$benId")
                    return@withContext null
                }
                removeAllTemporaryBenImages(context)
                Uri.fromFile(targetFile).toString()
            } catch (e: Exception) {
                Timber.e(e, "ImageUtils: Failed to save/compress image")
                null
            }
        }
    }

    private fun removeAllTemporaryBenImages(context: Context) {
        context.cacheDir.absoluteFile.listFiles { file ->
            file.name.startsWith(Konstants.tempBenImagePrefix)
        }?.forEach {
            it.delete()
        }
    }

    private fun removeAllStoredBenImages(context: Context) {
        context.filesDir.absoluteFile.listFiles { file ->
            file.name.isDigitsOnly() && file.name.endsWith("jpeg")
        }?.forEach {
            it.delete()
        }
    }

    fun removeAllBenImages(context: Context) {
        removeAllStoredBenImages(context)
        removeAllTemporaryBenImages(context)
    }

    suspend fun saveBenImageFromServerToStorage(
        context: Context, encodedString: String, benId: Long
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val imageBytes = decodeServerImageBytes(encodedString)
                if (imageBytes == null) {
                    Timber.d(
                        "ImageUtils: benId=$benId server image skipped, payloadChars=${encodedString.trim().length}"
                    )
                    return@withContext null
                }
                val targetFile = File(context.filesDir, "${benId}.jpeg")
                val tempFile = File(context.cacheDir, "srv_${benId}.img")
                try {
                    FileOutputStream(tempFile).use {
                        it.write(imageBytes)
                        it.flush()
                    }
                    val compressed = compressToJpeg(
                        context,
                        Uri.fromFile(tempFile),
                        targetFile,
                        label = "benId=$benId server"
                    )
                    if (!compressed) return@withContext null
                    Uri.fromFile(targetFile).toString()
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "ImageUtils: benId=$benId server image save failed")
                null
            }
        }
    }

    fun getEncodedStringForBenImage(context: Context, beneficiaryId: Long): String? {
        val file = File(context.filesDir, "${beneficiaryId}.jpeg")
        if (!file.exists() || file.length() == 0L) return null
        if (file.length() > MAX_REGISTRATION_IMAGE_BYTES) {
            compressToJpeg(context, Uri.fromFile(file), file, label = "benId=$beneficiaryId push")
        }
        if (!file.exists() || file.length() == 0L) return null
        FileInputStream(file).use { inputStream ->
            return Base64.encodeToString(inputStream.readBytes(), Base64.DEFAULT)
        }
    }

    fun renameImage(context: Context, oldBenId: Long, newBenId: Long): String? {
        val originalFile = File(context.filesDir, "${oldBenId}.jpeg")
        if (!originalFile.exists())
            return null
        val renamedFile = File(context.filesDir, "${newBenId}.jpeg")
        originalFile.apply {
            if (this.exists())
                this.renameTo(renamedFile)

        }
        return Uri.fromFile(renamedFile).toString()
    }

    /**
     * Writes a JPEG that stays at or under [MAX_REGISTRATION_IMAGE_BYTES].
     * Photos that are already small enough are copied as-is so quality is not
     * reduced a second time. Larger photos are scaled and re-encoded, keeping
     * the highest quality that still fits the cap.
     */
    fun compressToJpeg(
        context: Context,
        source: Uri,
        destination: File,
        label: String = "image"
    ): Boolean {
        return try {
            val sourceBytes = readUriBytes(context, source) ?: return false
            val saved = writeCompressedJpeg(sourceBytes, destination)
            if (saved) {
                Timber.i(
                    "ImageUtils: $label original=${formatKb(sourceBytes.size.toLong())} " +
                        "saved=${formatKb(destination.length())}"
                )
            } else {
                Timber.e(
                    "ImageUtils: $label compress failed original=${formatKb(sourceBytes.size.toLong())} " +
                        "header=${headerHex(sourceBytes)}"
                )
            }
            saved
        } catch (e: Exception) {
            Timber.e(e, "ImageUtils: compressToJpeg failed for $label")
            false
        }
    }

    private fun decodeServerImageBytes(encodedString: String): ByteArray? {
        val trimmed = encodedString.trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null

        val payload = if (trimmed.startsWith("data:", ignoreCase = true)) {
            trimmed.substringAfter(",", missingDelimiterValue = "")
        } else {
            trimmed
        }
        if (payload.isBlank()) return null
        if (payload.startsWith("http://", true) || payload.startsWith("https://", true)) {
            Timber.w("ImageUtils: server image is a URL, not image bytes")
            return null
        }

        val decoded = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrElse {
            Timber.e(it, "ImageUtils: server image is not valid base64")
            return null
        }
        if (decoded.size < MIN_IMAGE_BYTES) return null
        if (looksLikeImage(decoded)) return decoded

        val nested = runCatching { String(decoded, Charsets.US_ASCII).trim() }.getOrNull()
        if (!nested.isNullOrEmpty() && nested.length > MIN_IMAGE_BYTES) {
            val twice = runCatching { Base64.decode(nested, Base64.DEFAULT) }.getOrNull()
            if (twice != null && looksLikeImage(twice)) return twice
        }
        Timber.w(
            "ImageUtils: server payload is not a photo. payloadChars=${payload.length} " +
                "decoded=${formatKb(decoded.size.toLong())} header=${headerHex(decoded)} " +
                "prefix=${payload.take(40)}"
        )
        return null
    }

    private fun writeCompressedJpeg(sourceBytes: ByteArray, destination: File): Boolean {
        if (!looksLikeImage(sourceBytes)) return false
        val rotation = readRotationDegrees(sourceBytes)
        if (sourceBytes.size <= MAX_REGISTRATION_IMAGE_BYTES && rotation == 0) {
            writeReplacing(destination, sourceBytes)
            return destination.length() in 1..MAX_REGISTRATION_IMAGE_BYTES
        }

        val bitmap = decodeOriented(sourceBytes, rotation) ?: return false
        try {
            val encoded = encodeUnderLimit(bitmap, MAX_REGISTRATION_IMAGE_BYTES)
            if (encoded.isEmpty() || encoded.size > MAX_REGISTRATION_IMAGE_BYTES) return false
            writeReplacing(destination, encoded)
            return destination.length() in 1..MAX_REGISTRATION_IMAGE_BYTES
        } finally {
            bitmap.recycle()
        }
    }

    private fun readUriBytes(context: Context, source: Uri): ByteArray? {
        if (source.scheme == "file") {
            val path = source.path ?: return null
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            return file.readBytes()
        }
        return context.contentResolver.openInputStream(source)?.use { it.readBytes() }
    }

    private fun readRotationDegrees(bytes: ByteArray): Int {
        return runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
        }.getOrNull() ?: 0
    }

    private fun decodeOriented(bytes: ByteArray, rotation: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= INITIAL_LONGEST_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inJustDecodeBounds = false
        }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val scaled = scaleToLongestEdge(bitmap, INITIAL_LONGEST_EDGE)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun scaleToLongestEdge(source: Bitmap, longestEdge: Int): Bitmap {
        val current = maxOf(source.width, source.height)
        if (current <= longestEdge) return source
        val scale = longestEdge.toFloat() / current
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun encodeUnderLimit(source: Bitmap, maxBytes: Int): ByteArray {
        var working = source
        var best = ByteArray(0)
        try {
            for (edge in LONGEST_EDGE_STEPS) {
                if (maxOf(working.width, working.height) > edge) {
                    val scaled = scaleToLongestEdge(working, edge)
                    if (working !== source) working.recycle()
                    working = scaled
                }
                var quality = START_JPEG_QUALITY
                while (quality >= MIN_JPEG_QUALITY) {
                    val encoded = ByteArrayOutputStream().use { stream ->
                        working.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                        stream.toByteArray()
                    }
                    best = encoded
                    if (encoded.size <= maxBytes) return encoded
                    quality -= JPEG_QUALITY_STEP
                }
            }
            return best
        } finally {
            if (working !== source) working.recycle()
        }
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_IMAGE_BYTES) return false
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) return true
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) return true
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return true
        return false
    }

    private fun headerHex(bytes: ByteArray): String {
        val count = minOf(8, bytes.size)
        return buildString(count * 2) {
            for (index in 0 until count) {
                append("%02X".format(Locale.US, bytes[index].toInt() and 0xFF))
            }
        }
    }

    private fun formatKb(bytes: Long): String =
        "%.1f KB".format(Locale.US, bytes / 1024.0)

    private fun writeReplacing(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.cmp")
        FileOutputStream(temp).use {
            it.write(bytes)
            it.flush()
        }
        if (destination.exists() && !destination.delete()) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
            return
        }
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
    }

    private const val MIN_IMAGE_BYTES = 32
    private const val INITIAL_LONGEST_EDGE = 1600
    private val LONGEST_EDGE_STEPS = intArrayOf(1600, 1280, 1024, 800, 640, 480)
    private const val START_JPEG_QUALITY = 85
    private const val MIN_JPEG_QUALITY = 55
    private const val JPEG_QUALITY_STEP = 5
}
