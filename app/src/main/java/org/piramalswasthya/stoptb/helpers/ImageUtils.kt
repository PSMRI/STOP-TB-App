package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.text.isDigitsOnly
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.Compression
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


object ImageUtils {

    /** Max stored/uploaded file size; keeps JSON body under typical nginx 1MB limits. */
    private const val BEN_IMAGE_MAX_BYTES = 200_000L

    private fun Compression.benImageConstraints() {
        resolution(640, 640)
        quality(60)
        size(BEN_IMAGE_MAX_BYTES)
    }

    private suspend fun compressBenImageFile(context: Context, targetFile: File): File? {
        if (!targetFile.exists() || targetFile.length() == 0L) return null
        return try {
            val compressedFile = Compressor.compress(context, targetFile) {
                benImageConstraints()
            }
            if (compressedFile.exists() && compressedFile.length() > 0) {
                compressedFile.copyTo(targetFile, overwrite = true)
                if (compressedFile.absolutePath != targetFile.absolutePath) {
                    compressedFile.delete()
                }
                targetFile
            } else {
                Timber.e("ImageUtils: Compression produced invalid file")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "ImageUtils: Failed to compress beneficiary image")
            null
        }
    }

    suspend fun saveBenImageFromCameraToStorage(
        context: Context,
        uriString: String,
        benId: Long
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Timber.e("ImageUtils: InputStream is null for uri=$uriString")
                    return@withContext null
                }
                val targetFile = File(context.filesDir, "${benId}.jpeg")
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    Timber.e("ImageUtils: Invalid image file. exists=${targetFile.exists()}, size=${targetFile.length()}")
                    return@withContext null
                }
                Timber.d("Uncompressed image: ${targetFile.absolutePath}, size=${targetFile.length()}")
                if (compressBenImageFile(context, targetFile) == null) {
                    return@withContext null
                }
                Timber.d("Compressed image: ${targetFile.absolutePath}, size=${targetFile.length()}")
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
                val inputByteArray = Base64.decode(encodedString, Base64.DEFAULT)
                val targetFile = File(context.filesDir, "${benId}.jpeg").also { it.createNewFile() }
                FileOutputStream(targetFile).use {
                    it.write(inputByteArray)
                    it.flush()
                }
                compressBenImageFile(context, targetFile)
                Timber.d("Compressed target file :->$targetFile ${targetFile.length()}")
                Uri.fromFile(targetFile).toString()

            } catch (e: java.lang.Exception) {
                Timber.d("Compress failed with error $e ${e.localizedMessage} ${e.stackTrace}")
                null
            }
        }
    }

    suspend fun getEncodedStringForBenImage(context: Context, beneficiaryId: Long): String? {
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "${beneficiaryId}.jpeg")
            if (!file.exists()) return@withContext null
            compressBenImageFile(context, file) ?: return@withContext null
            val byteArray = FileInputStream(file).use { it.readBytes() }
            Timber.d("ImageUtils: Uploading ben image benId=$beneficiaryId, bytes=${byteArray.size}")
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }
    }

    /**
     * Copies camera/gallery images from temporary content/cache URIs into permanent app storage
     * ({filesDir}/{benId}.jpeg). Already-persisted filesDir paths are returned unchanged.
     */
    suspend fun persistBenImagePathIfNeeded(
        context: Context,
        imagePath: String?,
        benId: Long
    ): String? {
        if (imagePath.isNullOrBlank()) return null

        val uri = Uri.parse(imagePath)
        when (uri.scheme?.lowercase()) {
            "content" -> {
                return saveBenImageFromCameraToStorage(context, imagePath, benId)
            }

            "file" -> {
                val file = File(uri.path ?: return saveBenImageFromCameraToStorage(context, imagePath, benId))
                return when {
                    file.absolutePath.startsWith(context.filesDir.absolutePath) && file.exists() ->
                        imagePath

                    file.absolutePath.startsWith(context.cacheDir.absolutePath) ->
                        saveBenImageFromCameraToStorage(context, imagePath, benId)

                    else -> saveBenImageFromCameraToStorage(context, imagePath, benId)
                }
            }
        }

        val file = File(imagePath)
        return when {
            file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath) ->
                Uri.fromFile(file).toString()

            file.exists() && file.absolutePath.startsWith(context.cacheDir.absolutePath) ->
                saveBenImageFromCameraToStorage(context, Uri.fromFile(file).toString(), benId)

            else -> {
                Timber.w("ImageUtils: Unrecognized image path, attempting save: $imagePath")
                saveBenImageFromCameraToStorage(context, imagePath, benId)
            }
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


}