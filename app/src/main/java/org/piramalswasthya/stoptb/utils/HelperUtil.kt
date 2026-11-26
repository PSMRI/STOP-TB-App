package org.piramalswasthya.stoptb.utils
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.collection.lruCache
import androidx.core.content.FileProvider
import androidx.core.graphics.withTranslation
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.LayoutMediaOptionsBinding
import org.piramalswasthya.stoptb.databinding.LayoutViewMediaBinding
import org.piramalswasthya.stoptb.helpers.Languages
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HelperUtil {

    fun Context.findFragmentActivity(): FragmentActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun setEnLocaleForDatePicker(activity: FragmentActivity){
        Locale.setDefault(Locale.ENGLISH)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        activity.resources.updateConfiguration(
            config,
            activity.resources.displayMetrics
        )
    }

    fun setOriginalLocaleForDatePicker(activity: FragmentActivity, originalLocale: Locale){
        Locale.setDefault(originalLocale)
        val restoreConfig = Configuration(activity.resources.configuration)
        restoreConfig.setLocale(originalLocale)
        activity.resources.updateConfiguration(
            restoreConfig,
            activity.resources.displayMetrics
        )
    }


    fun getLocalizedResources(context: Context, currentLanguage: Languages): Resources {
        val desiredLocale = Locale.forLanguageTag(currentLanguage.symbol)
        var conf = context.resources.configuration
        conf = Configuration(conf)
        conf.setLocale(desiredLocale)
        val localizedContext: Context = context.createConfigurationContext(conf)
        return localizedContext.resources
    }

    fun getDateStringFromLong(dateLong: Long?): String? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        dateLong?.let {
            val dateString = dateFormat.format(dateLong)
            return dateString
        } ?: run {
            return null
        }

    }

    /**
     * gets millis for date in dd-MM-yyyy format
     */
    fun getLongFromDate(dateString: String): Long {
        val f = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val date = f.parse(dateString)
        return date?.time ?: throw IllegalStateException("Invalid date for dateReg")
    }

    fun getDiffYears(a: Calendar, b: Calendar): Int {
        var diff = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        if (a.get(Calendar.MONTH) >= b.get(Calendar.MONTH)
            && a.get(
                Calendar.DAY_OF_MONTH
            ) > b.get(
                Calendar.DAY_OF_MONTH
            )
        ) {
            diff--
        }
        return diff
    }

    /**
     * get current date in yyyy-MM-dd HH:mm:ss format
     */
    fun getCurrentDate(millis: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        val dateString = dateFormat.format(millis)
        val timeString = timeFormat.format(millis)
        return "${dateString}T${timeString}.000Z"
    }

    /**
     * get date string in yyyy-MM-dd format from given long date
     */
    fun getDateStrFromLong(dateLong: Long?): String? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        dateLong?.let {
            if (dateLong == 0L) return null
            val dateString = dateFormat.format(dateLong)
            val timeString = timeFormat.format(dateLong)
            return dateString
        } ?: run {
            return null
        }

    }

    fun getDateTimeStringFromLong(dateLong: Long?): String? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        dateLong?.let {
            if (dateLong == 0L) return null
            val dateString = dateFormat.format(dateLong)
            val timeString = timeFormat.format(dateLong)
            return "${dateString}T${timeString}.000Z"
        } ?: run {
            return null
        }

    }

    fun getLongFromDateStr(dateString: String?): Long {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val date = dateString?.let { f.parse(it) }
        return date?.time ?: 0L
    }

    fun Canvas.drawMultilineText(
        text: CharSequence,
        textPaint: TextPaint,
        width: Int,
        x: Float,
        y: Float,
        start: Int = 0,
        end: Int = text.length,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        spacingMult: Float = 1f,
        spacingAdd: Float = 0f,
        includePad: Boolean = true,
        ellipsizedWidth: Int = width,
        ellipsize: TextUtils.TruncateAt? = null
    ) {

        val cacheKey = "$text-$start-$end-$textPaint-$width-$alignment-" +
                "$spacingMult-$spacingAdd-$includePad-$ellipsizedWidth-$ellipsize"

        // minSdk is 25, so the builder (API 23+) is always available.
        val staticLayout =
            StaticLayoutCache[cacheKey] ?: StaticLayout.Builder.obtain(text, start, end, textPaint, width)
                .setAlignment(alignment)
                .setLineSpacing(spacingAdd, spacingMult)
                .setIncludePad(includePad)
                .setEllipsizedWidth(ellipsizedWidth)
                .setEllipsize(ellipsize)
                .build()

        staticLayout.draw(this, x, y)
    }

    private fun StaticLayout.draw(canvas: Canvas, x: Float, y: Float) {
        canvas.withTranslation(x, y) {
            draw(this)
        }
    }

    private object StaticLayoutCache {

        private const val MAX_SIZE = 50 // Arbitrary max number of cached items
        private val cache = lruCache<String, StaticLayout>(MAX_SIZE)

        operator fun set(key: String, staticLayout: StaticLayout) {
            cache.put(key, staticLayout)
        }

        operator fun get(key: String): StaticLayout? {
            return cache[key]
        }
    }

    fun convertDpToPixel(dp: Float, context: Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    fun isValidName(name: String): Boolean {
        val regex = Regex("^[a-zA-Z][a-zA-Z\\s'-]*[a-zA-Z]$")
        return regex.matches(name.trim())
    }


    fun showReminderDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        cancelable: Boolean = false,
        context: Context
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .apply {
                setPositiveButton(positiveText) { dialog, _ ->
                    dialog.dismiss(); onPositive?.invoke()
                }
                negativeText?.let {
                    setNegativeButton(it) { dialog, _ ->
                        dialog.dismiss(); onNegative?.invoke()
                    }
                }
            }
            .setCancelable(cancelable)
            .show()
    }

    fun Context.showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    fun getFileName(uri: android.net.Uri, appContext: Context): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } else {
            uri.path?.let { path -> File(path).name }
        }
    }
    fun MutableMap<Int, Uri?>.hasUploadedFile(): Boolean =
        values.any { it != null }

    fun Context.createTempImageUri(): Uri {
        val tmpFile = File.createTempFile("uwin_img_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(this, "$packageName.provider", tmpFile)
    }

    fun Context.showImageDialog(uri: Uri) {
        val binding = LayoutViewMediaBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        Glide.with(this).load(uri).placeholder(R.drawable.ic_person)
            .into(binding.viewImage)

        binding.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun Context.showMediaOptionsDialog(
        onCameraClick: () -> Unit,
        onGalleryClick: () -> Unit
    ) {
        val binding = LayoutMediaOptionsBinding.inflate(LayoutInflater.from(this))
        binding.btnPdf.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnCamera.setOnClickListener { dialog.dismiss(); onCameraClick() }
        binding.btnGallery.setOnClickListener { dialog.dismiss(); onGalleryClick() }
        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    fun Context.showUploadReminderDialog(
        message: String,
        onNo: () -> Unit
    ) {
        showReminderDialog(
            title = getString(R.string.reminder),
            message = message,
            positiveText = getString(R.string.yes_dialog),
            negativeText = getString(R.string.no_dialog),
            onPositive = {},
            onNegative = onNo,
            context = this
        )
    }

    fun launchCamera(context: Context): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }

    fun Context.getFileSizeInMB(uri: Uri): Double? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val sizeInBytes = pfd.statSize
                if (sizeInBytes > 0) sizeInBytes / (1024.0 * 1024.0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun Long.toRequestBody(): RequestBody =
        this.toString().toRequestBody("text/plain".toMediaType())

    fun String.toRequestBody(): RequestBody =
        this.toRequestBody("text/plain".toMediaType())

    fun getAgeStrFromAgeUnit(ageUnitDTO: org.piramalswasthya.stoptb.model.AgeUnitDTO): String {
        return when {
            ageUnitDTO.years > 0 -> "${ageUnitDTO.years} Yr ${ageUnitDTO.months} Mo ${ageUnitDTO.days} D"
            ageUnitDTO.months > 0 -> "${ageUnitDTO.months} Mo ${ageUnitDTO.days} D"
            else -> "${ageUnitDTO.days} D"
        }
    }

    fun getDobFromAge(ageUnitDTO: org.piramalswasthya.stoptb.model.AgeUnitDTO): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.YEAR, -ageUnitDTO.years)
        cal.add(java.util.Calendar.MONTH, -ageUnitDTO.months)
        cal.add(java.util.Calendar.DAY_OF_MONTH, -ageUnitDTO.days)
        return cal.timeInMillis
    }

    fun updateAgeDTO(ageUnitDTO: org.piramalswasthya.stoptb.model.AgeUnitDTO, cal: java.util.Calendar) {
        val now = java.util.Calendar.getInstance()
        var years = now.get(java.util.Calendar.YEAR) - cal.get(java.util.Calendar.YEAR)
        var months = now.get(java.util.Calendar.MONTH) - cal.get(java.util.Calendar.MONTH)
        var days = now.get(java.util.Calendar.DAY_OF_MONTH) - cal.get(java.util.Calendar.DAY_OF_MONTH)
        if (days < 0) { months--; days += cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) }
        if (months < 0) { years--; months += 12 }
        ageUnitDTO.years = years
        ageUnitDTO.months = months
        ageUnitDTO.days = days
    }

}

fun String?.toGpsTimestampLong(): Long? {
    if (this.isNullOrBlank()) return null
    val parsedLong = this.toLongOrNull()
    if (parsedLong != null) return parsedLong

    val patterns = listOf(
        "MMM dd, yyyy h:mm:ss a",
        "MMM d, yyyy h:mm:ss a",
        "MMM dd, yyyy HH:mm:ss a",
        "MMM d, yyyy HH:mm:ss a",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "dd-MM-yyyy HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
            val date = sdf.parse(this)
            if (date != null) {
                return date.time
            }
        } catch (_: Exception) {
        }
    }
    return null
}
// Standalone AgeUnitDTO for AgePicker
