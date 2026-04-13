package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.text.isDigitsOnly
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.BenBasicDomainForForm
import org.piramalswasthya.stoptb.model.BenWithCbacReferDomain
import org.piramalswasthya.stoptb.model.BenWithMalariaConfirmedDomain
import org.piramalswasthya.stoptb.model.BenWithTbScreeningDomain
import org.piramalswasthya.stoptb.model.BenWithTbSuspectedDomain
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


fun filterBenList(list: List<BenBasicDomain>, text: String): List<BenBasicDomain> {
    if (text == "") return list
    val filterText = text.lowercase()
    return list.filter { filterForBen(it, filterText) }
}

fun filterBenList(list: List<BenBasicDomain>, rchPresent: Boolean) =
    if (rchPresent) {
        list.filter {
            it.rchId.takeIf { it1 -> it1.toString().isDigitsOnly() }?.contains("") ?: false
        }
    } else list

fun filterBenList(list: List<BenBasicDomain>, filterType: Int): List<BenBasicDomain> {
    return when (filterType) {
        1 -> list.filter { !it.abhaId.isNullOrEmpty() }
        2 -> list.filter { it.abhaId.isNullOrEmpty() }
        3 -> list.filter { ben -> getAgeFromDob(ben.dob) >= 30 && ben.isDeathValue == "false" }
        4 -> list.filter(::isWARA)
        else -> list
    }
}

private fun isWARA(ben: BenBasicDomain): Boolean {
    val age = getAgeFromDob(ben.dob)
    val alive = ben.isDeathValue.equals("false", ignoreCase = true)
    val genderOk = ben.gender?.equals("female", ignoreCase = true) == true
    val reproOk = (ben.reproductiveStatusId == 1 || ben.reproductiveStatusId == 2)
    return genderOk && alive && age in 20..49 && reproOk
}

fun getAgeFromDob(dob: Long?): Int {
    if (dob == null) return 0
    val diffMillis = System.currentTimeMillis() - dob
    return (diffMillis / (1000L * 60 * 60 * 24 * 365)).toInt()
}

fun filterForBen(ben: BenBasicDomain, filterText: String) =
    ben.hhId.toString().lowercase().contains(filterText) ||
            ben.benId.toString().lowercase().contains(filterText.replace(" ", "")) ||
            ben.abhaId.toString().replace("-", "").lowercase().contains(filterText.replace(" ", "")) ||
            ben.regDate.lowercase().contains(filterText) ||
            ben.age.lowercase() == filterText.lowercase() ||
            ben.benFullName.lowercase().contains(filterText) ||
            ben.familyHeadName.lowercase().contains(filterText) ||
            ben.benSurname?.lowercase()?.contains(filterText) ?: false ||
            ben.rchId.takeIf { it?.isDigitsOnly() == true }?.contains(filterText.replace(" ", "")) ?: false ||
            ben.mobileNo.lowercase().contains(filterText.replace(" ", "")) ||
            ben.gender.lowercase() == filterText.lowercase() ||
            ben.spouseName?.lowercase()?.contains(filterText) == true ||
            ben.fatherName?.lowercase()?.contains(filterText) ?: false

@JvmName("filterBenList1")
fun filterBenFormList(list: List<BenBasicDomainForForm>, text: String): List<BenBasicDomainForForm> {
    if (text == "") return list
    val filterText = text.lowercase()
    return list.filter {
        it.hhId.toString().lowercase().contains(filterText) ||
                it.benId.toString().lowercase().contains(filterText) ||
                it.regDate.lowercase().contains(filterText) ||
                it.age.lowercase().contains(filterText) ||
                it.rchId.takeIf { it1 -> it1?.isDigitsOnly() == true }?.contains(filterText) ?: false ||
                it.benName.lowercase().contains(filterText) ||
                it.familyHeadName.lowercase().contains(filterText) ||
                it.spouseName?.lowercase()?.contains(filterText) == true ||
                it.benSurname?.lowercase()?.contains(filterText) ?: false ||
                it.dateOfDeath?.lowercase()?.contains(filterText) ?: false ||
                it.mobileNo.lowercase().contains(filterText) ||
                it.gender.lowercase().contains(filterText) ||
                it.fatherName?.lowercase()?.contains(filterText) ?: false
    }
}

fun filterTbScreeningList(list: List<BenWithTbScreeningDomain>, filterText: String) =
    list.filter {
        it.ben.benId.toString().lowercase().contains(filterText) ||
                it.ben.age.lowercase().contains(filterText) ||
                it.ben.familyHeadName.lowercase().contains(filterText) ||
                it.ben.benFullName.lowercase().contains(filterText) ||
                it.ben.spouseName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.fatherName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.mobileNo.lowercase().contains(filterText) ||
                it.ben.gender.lowercase().contains(filterText) ||
                it.ben.rchId.takeIf { it1 -> it1?.isDigitsOnly() == true }?.contains(filterText) ?: false
    }

fun filterTbSuspectedList(list: List<BenWithTbSuspectedDomain>, filterText: String) =
    list.filter {
        it.ben.benId.toString().lowercase().contains(filterText) ||
                it.ben.age.lowercase().contains(filterText) ||
                it.ben.familyHeadName.lowercase().contains(filterText) ||
                it.ben.benFullName.lowercase().contains(filterText) ||
                it.ben.spouseName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.fatherName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.mobileNo.lowercase().contains(filterText) ||
                it.ben.gender.lowercase().contains(filterText) ||
                it.ben.rchId.takeIf { it1 -> it1?.isDigitsOnly() == true }?.contains(filterText) ?: false
    }

fun filterMalariaConfirmedList(list: List<BenWithMalariaConfirmedDomain>, filterText: String) =
    list.filter {
        it.ben.benId.toString().lowercase().contains(filterText) ||
                it.ben.age.lowercase().contains(filterText) ||
                it.ben.familyHeadName.lowercase().contains(filterText) ||
                it.ben.benFullName.lowercase().contains(filterText) ||
                it.ben.spouseName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.fatherName?.lowercase()?.contains(filterText) ?: false ||
                it.ben.mobileNo.lowercase().contains(filterText) ||
                it.ben.gender.lowercase().contains(filterText) ||
                it.ben.rchId.takeIf { it1 -> it1.toString().isDigitsOnly() }?.contains(filterText) ?: false
    }

enum class EcFilterType {
    NEWEST_FIRST, OLDEST_FIRST, AGE_WISE, SYNCING_FIRST, UNSYNCED_FIRST
}

fun sortHwcList(list: List<BenWithCbacReferDomain>, sort: EcFilterType): List<BenWithCbacReferDomain> =
    when (sort) {
        EcFilterType.NEWEST_FIRST   -> list.sortedByDescending { it.referalCac.revisitDate }
        EcFilterType.OLDEST_FIRST   -> list.sortedBy { it.referalCac.revisitDate }
        EcFilterType.AGE_WISE       -> list.sortedBy { it.ben.dob }
        EcFilterType.SYNCING_FIRST  -> list.sortedBy { if (it.referalCac.syncState == SyncState.SYNCING) 0 else 1 }
        EcFilterType.UNSYNCED_FIRST -> list.sortedBy { if (it.referalCac.syncState == SyncState.UNSYNCED) 0 else 1 }
    }

fun getWeeksOfPregnancy(regLong: Long, lmpLong: Long?): Int {
    return lmpLong?.let {
        (TimeUnit.MILLISECONDS.toDays(regLong - it) / 7).toInt()
    } ?: 0
}

fun getTodayMillis() = Calendar.getInstance().setToStartOfTheDay().timeInMillis

fun Calendar.setToStartOfTheDay() = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

fun Calendar.setToEndOfTheDay() = apply {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 0)
}

fun getDateFromLong(time: Long): Date = Date(time)

fun getPatientTypeByAge(dateOfBirth: Date): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val birthDate = dateOfBirth.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val currentDate = LocalDate.now()
        val period = Period.between(birthDate, currentDate)
        val years = period.years
        val months = period.months
        val days = period.days
        when {
            years == 0 && months == 0 && days <= 30 -> "new_born_baby"
            years == 0 && (months > 0 || days > 30) -> "infant"
            years in 1..12 -> "child"
            years in 13..18 -> "adolescence"
            else -> "adult"
        }
    } else {
        val current = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = dateOfBirth }
        var years = current.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        var months = current.get(Calendar.MONTH) - birth.get(Calendar.MONTH)
        var days = current.get(Calendar.DAY_OF_MONTH) - birth.get(Calendar.DAY_OF_MONTH)
        if (days < 0) { months -= 1; days += current.getActualMaximum(Calendar.DAY_OF_MONTH) }
        if (months < 0) { years -= 1; months += 12 }
        when {
            years == 0 && months <= 1 -> "new_born_baby"
            years == 0 && months <= 12 -> "infant"
            years in 1..12 -> "child"
            years in 13..18 -> "adolescence"
            else -> "adult"
        }
    }
}

sealed class NetworkResponse<T>(val data: T? = null, val message: String? = null) {
    class Idle<T> : NetworkResponse<T>(null, null)
    class Loading<T> : NetworkResponse<T>(null, null)
    class Success<T>(data: T) : NetworkResponse<T>(data = data)
    class Error<T>(message: String) : NetworkResponse<T>(data = null, message = message)
}

fun getDateString(dateLong: Long?): String? {
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
    return dateLong?.let { dateFormat.format(Date(it)) }
}

@Suppress("deprecation")
fun isInternetAvailable(activity: Context): Boolean {
    val conMgr = activity.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = conMgr.activeNetwork
        val networkCapabilities = conMgr.getNetworkCapabilities(network)
        networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    } else {
        conMgr.activeNetworkInfo != null && conMgr.activeNetworkInfo!!.isAvailable && conMgr.activeNetworkInfo!!.isConnected
    }
}