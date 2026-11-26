package org.piramalswasthya.stoptb.model

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import org.piramalswasthya.stoptb.configuration.FormDataModel
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.model.BenBasicCache.Companion.getAgeFromDob
import org.piramalswasthya.stoptb.model.BenBasicCache.Companion.getAgeUnitFromDob
import org.piramalswasthya.stoptb.utils.HelperUtil.getDateStringFromLong
import org.piramalswasthya.stoptb.utils.toGpsTimestampLong
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

//enum class TypeOfList {
//    INFANT,
//    CHILD,
//    ADOLESCENT,
//    GENERAL,
//    ELIGIBLE_COUPLE,
//    ANTENATAL_MOTHER,
//    DELIVERY_STAGE,
//    POSTNATAL_MOTHER,
//    MENOPAUSE,
//    TEENAGER,
//    OTHER,
//
//}

enum class AgeUnit {
    DAYS,
    MONTHS,
    YEARS
}

enum class Gender {
    MALE,
    FEMALE,
    TRANSGENDER,
    PREFER_NOT_TO_SAY
}

enum class BenStatus {
    Alive,
    Death,
}

enum class ScreeningStatus {
    UNSCREENED,
    SYMPTOMS_SCREENED,
    CHEST_XRAY_DONE,
    TRUNAT_TEST_DONE
}


/**
 * The three 1:N tables (CBAC, TB_SCREENING, TB_SUSPECTED) are read with scalar subqueries rather
 * than LEFT JOINs. The joins could return several rows per beneficiary, which forced
 * "GROUP BY b.beneficiaryId" to collapse them - and a view carrying GROUP BY (plus an ORDER BY
 * every caller immediately overrides) cannot be flattened by SQLite. Every query against this
 * view therefore built and sorted the whole thing before the caller's WHERE or LIMIT applied, so
 * ind_ben_village_deactivate was unreachable and cost scaled with the table, not the page.
 *
 * Without the 1:N joins there is nothing to collapse, so GROUP BY and ORDER BY are gone and the
 * view flattens. Verified identical at 5,000 rows: same row count, same values in all 92 columns.
 * Where a beneficiary has several screening/suspected rows the *_syncState columns now take the
 * highest id (the most recently inserted) - a deliberate rule, where GROUP BY picked arbitrarily.
 *
 * The view deliberately reads only BENEFICIARY and HOUSEHOLD.
 *
 * It used to carry cbacFilled/cbacSyncState/tbsnFilled/tbsnSyncState/tbspFilled/tbspSyncState as
 * correlated subqueries into CBAC, TB_SCREENING and TB_SUSPECTED. Room resolves a view down to its
 * source tables and registers an invalidation observer on every one of them, so *any* write to
 * those three tables invalidated *every* query over this view - including the Paging sources
 * behind the beneficiary lists. Measured on a camp device: while the TB Suspected push drained its
 * backlog (one chunk every ~2s), the All Beneficiaries list rebuilt its PagingSource 83 times in
 * 171s and never finished loading, even though the query itself costs 54ms.
 *
 * Nothing in Kotlin ever read those six columns - they only appeared in SQL predicates - so they
 * are literals now, matching the dozen other placeholder columns already in this view. The
 * handful of queries that genuinely need "does this beneficiary have a screening row" test
 * TB_SCREENING directly and so observe it on their own, which is correct: those screens should
 * refresh on a screening write. A plain beneficiary list should not.
 */

@DatabaseView(
    viewName = "BEN_BASIC_CACHE",
    value = "SELECT b.beneficiaryId as benId,b.isMarried,b.noOfAliveChildren, b.noOfChildren, b.doYouHavechildren ,b.isConsent as isConsent, b.motherName as motherName, b.householdId as hhId, b.regDate, b.createdDate, b.updatedDate, b.firstName as benName, b.lastName as benSurname, b.gender, b.dob as dob,b.isDeactivate, b.isDeath,b.isDeathValue,b.dateOfDeath,b.timeOfDeath,b.reasonOfDeath,b.reasonOfDeathId,b.placeOfDeath,b.placeOfDeathId,b.otherPlaceOfDeath,b.isSpouseAdded,b.isChildrenAdded, b.familyHeadRelationPosition as relToHeadId" +
            ", b.contactNumber as mobileNo, b.fatherName,IFNULL(h.fam_familyHeadName,'') as familyHeadName, b.gen_spouseName as spouseName, b.rchId, b.nikshayId, b.gen_lastMenstrualPeriod as lastMenstrualPeriod" +
            ", b.isHrpStatus as hrpStatus, b.syncState, b.gen_reproductiveStatusId as reproductiveStatusId, b.isKid, b.immunizationStatus" +
            ", b.loc_village_id as villageId, b.loc_village_name as villageName, b.abha_healthIdNumber as abhaId" +
            ", b.isNewAbha" +
            // Literals, not subqueries - see the note above the view. Nothing in Kotlin reads
            // these six columns; they only ever appeared in SQL predicates, which now test the
            // source tables directly.
            ", 0 as cbacFilled, NULL as cbacSyncState" +
            ", 0 as cdrFilled, NULL as cdrSyncState" +
            ", 0 as mdsrFilled, NULL as mdsrSyncState" +
            ", NULL as pmsmaSyncState, 0 as pmsmaFilled" +
            ", 0 as hbncFilled" +
            ", 0 as hbycFilled" +
            ", 0 as pwrFilled, NULL as pwrSyncState" +
            ", NULL as doSyncState, NULL as irSyncState, NULL as crSyncState" +
            ", 0 as ecrFilled, 0 as ectFilled" +
            ", 0 as isMdsr" +
            ", 0 as tbsnFilled, NULL as tbsnSyncState" +
            ", 0 as tbspFilled, NULL as tbspSyncState" +
            ", 0 as hrppaFilled, 0 as hrpnpaFilled, 0 as hrpmbpFilled" +
            ", 0 as hrptFilled, 0 as hrptrackingDone, 0 as hrnptrackingDone, 0 as hrnptFilled" +
            ", NULL as hrppaSyncState, NULL as hrpnpaSyncState, NULL as hrpmbpSyncState, NULL as hrptSyncState, NULL as hrnptSyncState" +
            ", 0 as isDelivered, 0 as pwHrp" +
            ", 0 as irFilled, 0 as crFilled, 0 as doFilled" +
            ", b.isNonHH" +
            ", b.isAvailableForCamp" +
            ", b.reasonForNotAttendingCamp" +
            ", b.otherReasonForNotAttendingCamp" +
            ", b.screeningStatus" +
            ", b.symptomsScreenedDate" +
            ", b.chestXrayDoneDate" +
            ", b.trunatTestDoneDate" +
            ", b.placeOfCurrentLiving" +
            ", b.otherPlaceOfCurrentLiving" +
            ", b.institutionName" +
            " FROM BENEFICIARY b " +
            "LEFT JOIN HOUSEHOLD h ON b.householdId = h.householdId " +
            // No ORDER BY in the view. It made SQLite sort every non-draft beneficiary under
            // every COUNT and every query on the view - the dominant cost at 100K+. List queries
            // that relied on it now say ORDER BY <alias>.updatedDate DESC themselves (updatedDate
            // is exposed for that). GROUP BY stays dropped: the join is on HOUSEHOLD's primary
            // key, so it cannot multiply rows. Door-locked households stay hidden (v70).
            "WHERE b.isDraft = 0 AND IFNULL(h.isDoorLocked, 0) = 0"
)
@Parcelize
data class BenBasicCache(
    @ColumnInfo
    val benId: Long,
    val hhId: Long? = null,
    val regDate: Long,
    val createdDate: Long? = null,
    /** Exposed by the view so list queries can order by it (v63). */
    val updatedDate: Long? = null,
    var isDeath: Boolean = false,
    var isDeathValue: String? = null,
    var dateOfDeath: String? = null,
    var timeOfDeath: String? = null,
    var reasonOfDeath: String? = null,
    var reasonOfDeathId: Int? = null,
    var placeOfDeath: String? = null,
    var placeOfDeathId: Int? = null,
    var otherPlaceOfDeath: String? = null,
    val benName: String?,
    val benSurname: String? = null,
    val gender: Gender,
    val dob: Long,
    val relToHeadId: Int,
    val mobileNo: Long,
    val fatherName: String? = null,
    val motherName: String?= null,
    val familyHeadName: String? = null,
//    val typeOfList: TypeOfList,
    val spouseName: String? = null,
    val rchId: String? = null,
    val nikshayId: String? = null,
    val hrpStatus: Boolean,
    val syncState: SyncState?,
    val reproductiveStatusId: Int,
    val lastMenstrualPeriod: Long?,
    val isKid: Boolean,
    val immunizationStatus: Boolean,
    val villageId: Int,
    val abhaId: String?,
    val isNewAbha: Boolean,
    val cbacFilled: Boolean,
    val cbacSyncState: SyncState?,
    val cdrFilled: Boolean,
    val cdrSyncState: SyncState?,
    val mdsrFilled: Boolean,
    val mdsrSyncState: SyncState?,
    val pmsmaSyncState: SyncState?,
    val pmsmaFilled: Boolean,
    val hbncFilled: Boolean,
    val hbycFilled: Boolean,
    val pwrFilled: Boolean,
    val pwrSyncState: SyncState?,
    val doSyncState: SyncState?,
    val irSyncState: SyncState?,
    val crSyncState: SyncState?,
    val ecrFilled: Boolean,
    val ectFilled: Boolean,
    val tbsnFilled: Boolean,
    val tbsnSyncState: SyncState?,
    val tbspFilled: Boolean,
    val tbspSyncState: SyncState?,
    val hrppaFilled: Boolean,
    val hrpnpaFilled: Boolean,
    val hrpmbpFilled: Boolean,
    val hrptFilled: Boolean,
    val hrptrackingDone: Boolean,
    val hrnptrackingDone: Boolean,
    val hrnptFilled: Boolean,
    val hrppaSyncState: SyncState?,
    val hrpnpaSyncState: SyncState?,
    val hrpmbpSyncState: SyncState?,
    val hrptSyncState: SyncState?,
    val hrnptSyncState: SyncState?,
    val isDelivered: Boolean,
    val pwHrp: Boolean,
    val irFilled: Boolean,
    val isMdsr: Boolean,
    val crFilled: Boolean,
    val doFilled: Boolean,
    val isConsent: Boolean,
    var isSpouseAdded: Boolean,
    var isChildrenAdded: Boolean,
    var isMarried: Boolean,
    var doYouHavechildren: Boolean = false,
    var noOfChildren: Int = 0,
    var noOfAliveChildren: Int = 0,
    var isDeactivate: Boolean =false,
    val isNonHH: Boolean = false,
    val placeOfCurrentLiving: Int? = null,
    val isAvailableForCamp: Boolean = true,
    val reasonForNotAttendingCamp: String? = null,
    val otherReasonForNotAttendingCamp: String? = null,
    val screeningStatus: ScreeningStatus = ScreeningStatus.UNSCREENED,
    val symptomsScreenedDate: Long? = null,
    val chestXrayDoneDate: Long? = null,
    val trunatTestDoneDate: Long? = null,
    val otherPlaceOfCurrentLiving: String? = null,
    val institutionName: String? = null,
    val villageName: String? = null
) : Parcelable {
    companion object {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        fun getAgeFromDob(dob: Long): Int {
            val diffLong = System.currentTimeMillis() - dob
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffLong).toInt()
            if (diffDays < 31)
                return diffDays
            val yearDiff = (diffDays / 365)
            if (yearDiff > 0)
                return yearDiff
            val calDob = Calendar.getInstance()
            calDob.timeInMillis = dob
            val calNow = Calendar.getInstance()
            return (calNow.get(Calendar.YEAR) * 12 + calNow.get(Calendar.MONTH)) - (calDob.get(
                Calendar.YEAR
            ) * 12 + calDob.get(
                Calendar.MONTH
            )
                    )


        }

        fun getYearsFromDate(dateString: String): Int {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            val inputDate = sdf.parse(dateString) ?: return -1

            val dobCalendar = Calendar.getInstance()
            dobCalendar.time = inputDate
            val today = Calendar.getInstance()
            var years = today.get(Calendar.YEAR) - dobCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dobCalendar.get(Calendar.DAY_OF_YEAR)) {
                years--
            }

            return years
        }


        fun getAgeUnitFromDob(dob: Long): AgeUnit {
            val calDob = Calendar.getInstance().apply {
                timeInMillis = dob
            }
            val calNow = Calendar.getInstance()
            val diffYears = getDiffYears(calDob, calNow)
            val diffLong = System.currentTimeMillis() - dob
            return if (TimeUnit.MILLISECONDS.toDays(diffLong).toInt() in 0..31) {
                AgeUnit.DAYS
            } else if (diffYears == 0)
                AgeUnit.MONTHS
            else
                AgeUnit.YEARS

        }


        private fun getDiffYears(a: Calendar, b: Calendar): Int {
            var diff = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
            if (a.get(Calendar.MONTH) > b.get(Calendar.MONTH) || a.get(Calendar.MONTH) == b.get(
                    Calendar.MONTH
                ) && a.get(
                    Calendar.DAY_OF_MONTH
                ) > b.get(
                    Calendar.DAY_OF_MONTH
                )
            ) {
                diff--
            }
            return diff
        }


    }


    fun asBasicDomainModel(): BenBasicDomain {
        return BenBasicDomain(
            benId = benId,
            hhId = hhId ?: 0L,
            isDeath = isDeath,
            isDeathValue = isDeathValue,
            dateOfDeath = dateOfDeath,
            reproductiveStatusId = reproductiveStatusId,
            timeOfDeath = timeOfDeath,
            reasonOfDeath = reasonOfDeath,
            reasonOfDeathId = reasonOfDeathId,
            placeOfDeath = placeOfDeath,
            placeOfDeathId = placeOfDeathId,
            otherPlaceOfDeath = otherPlaceOfDeath,
            regDate = dateFormat.format(Date(regDate)),
            benName = benName.orEmpty(),
            benSurname = benSurname ?: "",
            gender = gender.name,
            dob = dob,
            abhaId = abhaId,
            isNewAbha = isNewAbha,
            relToHeadId = relToHeadId,
            mobileNo = mobileNo.toString(),
            motherName =  motherName?.takeIf { it.isNotEmpty() } ?: "Not Available",
            fatherName = fatherName?.takeIf { it.isNotEmpty() } ?: "Not Available",
            /*   motherName = motherName,*/
            familyHeadName = familyHeadName ?: "Not Available",
//            typeOfList = typeOfList.name,
            spouseName = spouseName?.takeIf { it.isNotEmpty() } ?: "Not Available",
            rchId = rchId?.takeIf { it.isNotEmpty() },
            nikshayId = nikshayId?.takeIf { it.isNotBlank() },
            hrpStatus = hrpStatus,
            syncState = syncState,
            isConsent = isConsent,
            isChildrenAdded = isChildrenAdded,
            isSpouseAdded = isSpouseAdded,
            isMarried = isMarried,
            noOfAliveChildren = noOfAliveChildren,
            noOfChildren = noOfChildren,
            doYouHavechildren = doYouHavechildren,
            isDeactivate = isDeactivate,
            isNonHH = isNonHH,
            placeOfCurrentLiving = placeOfCurrentLiving,
            otherPlaceOfCurrentLiving = otherPlaceOfCurrentLiving,
            institutionName = institutionName,
            screeningStatus = screeningStatus,
            symptomsScreenedDate = symptomsScreenedDate,
            chestXrayDoneDate = chestXrayDoneDate,
            trunatTestDoneDate = trunatTestDoneDate,
            villageName = villageName?.takeIf { it.isNotBlank() }
        )
    }

    fun asBasicDomainModelCHO(): BenBasicDomain {
        return BenBasicDomain(
            benId = benId,
            hhId = hhId ?: 0L,
            regDate = dateFormat.format(Date(regDate)),
            benName = benName.orEmpty(),
            benSurname = benSurname ?: "",
            spouseName = spouseName ?: "Not Available",
            gender = gender.name,
            dob = dob,
            abhaId = abhaId,
            mobileNo = mobileNo.toString(),
            fatherName = fatherName,
            familyHeadName = familyHeadName ?: "",
            rchId = rchId,
            nikshayId = nikshayId?.takeIf { it.isNotBlank() },
            hrpStatus = hrpStatus,
            relToHeadId = 0,
            syncState = syncState,
            isConsent = isConsent,
            isChildrenAdded =  isChildrenAdded,
            isSpouseAdded = isSpouseAdded,
            isMarried = isMarried,
            noOfAliveChildren = noOfAliveChildren,
            noOfChildren = noOfChildren,
            doYouHavechildren = doYouHavechildren,
            reproductiveStatusId = reproductiveStatusId,
            isNonHH = isNonHH,
            isAvailableForCamp = isAvailableForCamp,
            reasonForNotAttendingCamp = reasonForNotAttendingCamp,
            otherReasonForNotAttendingCamp = otherReasonForNotAttendingCamp,
            screeningStatus = screeningStatus,
            symptomsScreenedDate = symptomsScreenedDate,
            chestXrayDoneDate = chestXrayDoneDate,
            trunatTestDoneDate = trunatTestDoneDate,
            placeOfCurrentLiving = placeOfCurrentLiving,
            otherPlaceOfCurrentLiving = otherPlaceOfCurrentLiving,
            institutionName = institutionName,
            villageName = villageName?.takeIf { it.isNotBlank() }
        )
    }

}

fun getAgeDisplayString(dob: Long): String {
    if (dob == 0L) return "N/A"

    val calDob = Calendar.getInstance().apply { timeInMillis = dob }
    val calNow = Calendar.getInstance()

    val diffDays =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - dob).toInt()

    if (diffDays < 31) {
        return "$diffDays Day${if (diffDays != 1) "s" else ""}"
    }

    var years = calNow.get(Calendar.YEAR) - calDob.get(Calendar.YEAR)
    var months = calNow.get(Calendar.MONTH) - calDob.get(Calendar.MONTH)
    var days = calNow.get(Calendar.DAY_OF_MONTH) - calDob.get(Calendar.DAY_OF_MONTH)

    if (days < 0) {
        months -= 1
        val tempCal = calNow.clone() as Calendar
        tempCal.add(Calendar.MONTH, -1)
        days += tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    if (months < 0) {
        years -= 1
        months += 12
    }

    return when {
        years >= 1 -> {
            buildString {
                append("$years Year${if (years != 1) "s" else ""}")
                if (months >= 1) {
                    append(" $months Month${if (months != 1) "s" else ""}")
                }
                if (days >= 1) {
                    append(" $days Day${if (days != 1) "s" else ""}")
                }
            }
        }
        else -> {
            buildString {
                if (months >= 1) {
                    append("$months Month${if (months != 1) "s" else ""}")
                }
                if (days >= 1) {
                    append(" $days Day${if (days != 1) "s" else ""}")
                }
            }
        }
    }
}

fun BenRegCache.getAgeGenderDisplayString(): String {
    val fullAgeText = if (dob > 0L) {
        getAgeDisplayString(dob)
    } else {
        when (ageUnit) {
            AgeUnit.YEARS -> "$age Years"
            AgeUnit.MONTHS -> "$age Months"
            AgeUnit.DAYS -> "$age Days"
            else -> "N/A"
        }
    }

    // Keep form headers compact: show the largest available age unit only.
    val ageText = Regex("\\d+\\s+(?:Years|Year|Months|Month|Days|Day)")
        .find(fullAgeText)
        ?.value
        ?: fullAgeText

    val genderText = gender?.name
        ?.replace('_', ' ')
        ?.lowercase()
        ?.split(' ')
        ?.joinToString(" ") { part -> part.replaceFirstChar { ch -> ch.titlecase() } }
        ?: "N/A"

    return "$ageText | $genderText"
}



@Parcelize
data class BenBasicDomain(
    val benId: Long,
    val hhId: Long,

    var isDeath: Boolean = false,
    var isDeathValue: String? = null,
    var dateOfDeath: String? = null,
    var timeOfDeath: String? = null,
    var reasonOfDeath: String? = null,
    var reasonOfDeathId: Int? = null,
    var placeOfDeath: String? = null,
    var placeOfDeathId: Int? = null,
    var otherPlaceOfDeath: String? = null,
    var reproductiveStatusId: Int,

    val regDate: String,
    val benName: String,
    val benSurname: String? = null,
    val benFullName: String = "$benName $benSurname",
    val gender: String,
    val dob: Long,
    val ageInt: Int = getAgeFromDob(dob),
    val ageUnit: AgeUnit = getAgeUnitFromDob(dob),
//    val age: String = "$ageInt ${ageUnit.name}",
    val age: String = "${getAgeDisplayString(dob)}",
    val relToHeadId: Int,
    val mobileNo: String,
    val abhaId: String? = null,
    val isNewAbha: Boolean = false,
    val fatherName: String? = null,
    val motherName: String? = null,
    val familyHeadName: String,
    val spouseName: String? = null,
//    val typeOfList: String,
    val rchId: String? = null,
    val nikshayId: String? = null,
    val hrpStatus: Boolean = false,
    var syncState: SyncState?,
    val isConsent: Boolean,
    var isSpouseAdded: Boolean,
    var isChildrenAdded: Boolean,
    var isMarried : Boolean,
    var doYouHavechildren: Boolean = false,
    var noOfChildren: Int = 0,
    var noOfAliveChildren: Int = 0,
    var isDeactivate: Boolean =false,
    val isNonHH: Boolean = false,
    val isAvailableForCamp: Boolean = true,
    val reasonForNotAttendingCamp: String? = null,
    val otherReasonForNotAttendingCamp: String? = null,
    val screeningStatus: ScreeningStatus = ScreeningStatus.UNSCREENED,
    val symptomsScreenedDate: Long? = null,
    val chestXrayDoneDate: Long? = null,
    val trunatTestDoneDate: Long? = null,
    val placeOfCurrentLiving: Int? = null,
    val otherPlaceOfCurrentLiving: String? = null,
    val institutionName: String? = null,
    val villageName: String? = null

) : Parcelable{
    val dobString: String
        get() = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ENGLISH)
            .format(java.util.Date(dob))

    val nikshayIdDisplay: String
        get() = nikshayId?.takeIf { it.isNotBlank() } ?: "N/A"

    val isPlaceholderMobileNo: Boolean
        get() = mobileNo == "9999999999"

    val hasCallableMobileNo: Boolean
        get() = mobileNo.isNotBlank() && !isPlaceholderMobileNo

    val mobileNoDisplay: String
        get() = mobileNo.ifBlank { "N/A" }
}


data class BenChildCount(
    val benId: Long,
    val childCount: Int
)

data class HouseholdMemberCount(
    val hhId: Long,
    val memberCount: Int
)

data class HouseholdHeadName(
    val hhId: Long,
    val fullName: String
)

data class BenBasicDomainForForm(
    val benId: Long,
    val hhId: Long,
    var isDeath: Boolean = false,
    var isDeathValue: String? = null,
    var dateOfDeath: String? = null,
    var timeOfDeath: String? = null,
    var reasonOfDeath: String? = null,
    var reasonOfDeathId: Int? = null,
    var placeOfDeath: String? = null,
    var placeOfDeathId: Int? = null,
    var otherPlaceOfDeath: String? = null,
    val regDate: String,
    val benName: String,
    val benSurname: String? = null,
    val gender: String,
    val dob: Long,
    val ageInt: Int = getAgeFromDob(dob),
    val ageUnit: AgeUnit = getAgeUnitFromDob(dob),
    val age: String = "$ageInt $ageUnit",
    val mobileNo: String,
    val fatherName: String? = null,
    val spouseName: String? = null,
    val familyHeadName: String,
    val lastMenstrualPeriod: String? = null,
    val edd: String? = null,
//    val typeOfList: String,
    val rchId: String? = null,
    val hrpStatus: Boolean = false,
    val form1Filled: Boolean = false,
    val form2Filled: Boolean = false,
    val form3Filled: Boolean = false,
    var form1Enabled: Boolean = true,
    val form2Enabled: Boolean = true,
    val form3Enabled: Boolean = true,
    val formsFilled: Int = 0,
    var syncState: SyncState?,
    val isConsent: Boolean,

    ) {
    val mobileNoDisplay: String
        get() = mobileNo.takeIf { it.isNotBlank() && it != "9999999999" } ?: "N/A"

    companion object
}

data class BenRegKid(
    var childName: String? = null,
    var childRegisteredAWC: String? = null,
    var childRegisteredAWCId: Int = 0,
    var childRegisteredSchool: String? = null,
    var childRegisteredSchoolId: Int = 0,
    var typeOfSchool: String? = null,
    var typeOfSchoolId: Int = 0,
    var birthPlace: String? = null,
    var birthPlaceId: Int = 0,
    var facilityName: String? = null,
    var facilityId: Int = 0,
    var facilityOther: String? = null,
    var placeName: String? = null,
    var conductedDelivery: String? = null,
    var conductedDeliveryId: Int = 0,
    var conductedDeliveryOther: String? = null,
    var deliveryType: String? = null,
    var deliveryTypeId: Int = 0,
    var complications: String? = null,
    var complicationsId: Int = 0,
    var complicationsOther: String? = null,
    var term: String? = null,
    var termId: Int = 0,
    var gestationalAge: String? = null,
    var gestationalAgeId: Int = 0,
    var corticosteroidGivenMother: String? = null,
    var corticosteroidGivenMotherId: Int = 0,
    var criedImmediately: String? = null,
    var criedImmediatelyId: Int = 0,
    var birthDefects: String? = null,
    var birthDefectsId: Int = 0,
    var birthDefectsOthers: String? = null,
    var heightAtBirth: Double = 0.0,
    var weightAtBirth: Double = 0.0,
    var feedingStarted: String? = null,
    var feedingStartedId: Int = 0,
    var birthDosage: String? = null,
    var birthDosageId: Int = 0,
    var opvBatchNo: String? = null,
    var opvGivenDueDate: String? = null,
    var opvDate: String? = null,
    var bcdBatchNo: String? = null,
    var bcgGivenDueDate: String? = null,
    var bcgDate: String? = null,
    var hptBatchNo: String? = null,
    var hptGivenDueDate: String? = null,
    var hptDate: String? = null,
    var vitaminKBatchNo: String? = null,
    var vitaminKGivenDueDate: String? = null,
    var vitaminKDate: String? = null,
    var deliveryTypeOther: String? = null,
    var birthCertificateNumber: String? = null,
    var motherBenId: Long? = null,
    var childMotherName: String? = null,
    var motherPosition: Int? = null,
    var birthBCG: Boolean = false,
    var birthHepB: Boolean = false,
    var birthOPV: Boolean = false,
    val isConsent: Boolean = false,
    var birthCertificateFileFrontView: String? = null,
    var birthCertificateFileBackView: String? = null

)

@JsonClass(generateAdapter = true)
data class BenRegKidNetwork(
    val id: Int = 0,
    val benficieryid: Long,
    val childName: String? = null,
    val birthPlace: String? = null,
    val birthPlaceid: Int = 0,
    val facilityName: String? = null,
    val facilityid: Int = 0,
    val ashaid: Int = 0,
    val facilityOther: String? = null,
    val placeName: String? = null,
    val conductedDelivery: String? = null,
    val conductedDeliveryid: Int = 0,
    val conductedDeliveryOther: String? = null,
    val deliveryType: String? = null,
    val deliveryTypeid: Int = 0,
    val deliveryTypeOther: String? = null,
    @Json(name = "complecations")
    val complications: String? = null,
    @Json(name = "complecationsid")
    val complicationsid: Int = 0,
    val complicationsOther: String? = null,
    val term: String? = null,
    val termid: Int = 0,
    val gestationalAge: String? = null,
    val gestationalAgeid: Int = 0,
    val corticosteroidGivenMother: String? = null,
    val corticosteroidGivenMotherid: Int = 0,
    val criedImmediately: String? = null,
    val criedImmediatelyid: Int = 0,
    val birthDefects: String? = null,
    val birthDefectsid: Int = 0,
    val birthDefectsOthers: String? = null,
    val heightAtBirth: Int = 0,
    val weightAtBirth: Float = 0F,
    val feedingStarted: String? = null,
    val feedingStartedid: Int = 0,
    val birthDosage: String? = null,
    val birthDosageid: Int = 0,
    val opvBatchNo: String? = null,
    val opvGivenDueDate: String? = null,
    val opvDate: String? = null,
    val bcdBatchNo: String? = null,
    val bcgGivenDueDate: String? = null,
    val bcgDate: String? = null,
    val hptdBatchNo: String? = null,
    val hptGivenDueDate: String? = null,
    val hptDate: String? = null,
    val vitaminkBatchNo: String? = null,
    val vitaminkGivenDueDate: String? = null,
    val vitaminkDate: String? = null,

    val createdBy: String? = null,
    val createdDate: String? = null,
    val serverUpdatedStatus: Int = 0,
    val updatedBy: String? = null,
    val updatedDate: String? = null,

    val ProviderServiceMapID: Int = 0,
    val VanID: Int = 4,
    val Processed: String? = null,
    val Countyid: Int = 0,
    val stateid: Int = 0,
    val districtid: Int = 0,
    val districtname: String? = null,
    val villageid: Int = 0,

    val motherBenId: Long? = null,
    val motherName: String? = null,
    val motherposition: Int? = 0,


    val birthBCG: Boolean? = null,
    val birthHepB: Boolean? = null,
    val birthOPV: Boolean? = null,
    val isConsent: Boolean

)

data class BenHealthIdDetails(
    var healthId: String = "",
    var healthIdNumber: String = "",
    var isNewAbha: Boolean = false
)

data class BenRegGen(

    var maritalStatus: String? = null,
    var maritalStatusId: Int = 0,
    var spouseName: String? = null,
    var ageAtMarriage: Int = 0,
//    var dateOfMarriage: Long = 0,
    var marriageDate: Long? = null,
    //Menstrual details
//    var menstrualStatus: String? = null,
//    var menstrualStatusId: Int? = 0,
//    var regularityOfMenstrualCycle: String? = null,
//    var regularityOfMenstrualCycleId: Int = 0,
//    var lengthOfMenstrualCycle: String? = null,
//    var lengthOfMenstrualCycleId: Int = 0,
//    var menstrualBFD: String? = null,
//    var menstrualBFDId: Int = 0,
//    var menstrualProblem: String? = null,
//    var menstrualProblemId: Int = 0,
    var lastMenstrualPeriod: Long? = null,
    var reproductiveStatus: String? = null,
    var reproductiveStatusId: Int = 0,
//    var lastDeliveryConducted: String? = null,
//    var lastDeliveryConductedId: Int = 0,
//    var otherLastDeliveryConducted: String? = null,
//    var facilityName: String? = null,
//    var whoConductedDelivery: String? = null,
//    var whoConductedDeliveryId: Int = 0,
//    var noOfDaysForDelivery: Int? = null,
//    var otherWhoConductedDelivery: String? = null,
//    var deliveryDate: String? = null,
//    var expectedDateOfDelivery: Long? = null,
//    var numPreviousLiveBirth: Int = 0,
//    var formStatus: String? = null,
//    var formType: String? = null,
//    var ancCount: Int = 0,
//    var hrpCount: Int = 0,
//    var hrpSuspected: Boolean = false,
//    var isDeathStatus: Boolean = false,
)

@Entity(
    tableName = "BENEFICIARY",
    primaryKeys = ["beneficiaryId"],
//    foreignKeys = [
//        ForeignKey(
//            entity = HouseholdCache::class,
//            parentColumns = arrayOf("householdId"),
//            childColumns = arrayOf("householdId"),
//            onDelete = ForeignKey.CASCADE,
//            onUpdate = ForeignKey.CASCADE
//        )
//    ],
//    indices = [Index(name = "ind_ben", value = ["beneficiaryId"/*, "householdId"*/])]
    indices = [
        Index(name = "ind_ben_village", value = ["loc_village_id"]),
        // Push queries filter syncState + processed + isDraft; covers syncState-only lookups too (v62).
        Index(name = "ind_ben_sync_processed", value = ["syncState", "processed", "isDraft"]),
        // Village lists ordered by last update (v62).
        Index(name = "ind_ben_village_updated", value = ["loc_village_id", "isDeactivate", "updatedDate"]),
        Index(name = "ind_ben_draft_deactivate", value = ["isDraft", "isDeactivate"]),
        Index(name = "ind_ben_village_deactivate", value = ["loc_village_id", "isDeactivate", "isDraft"]),
        Index(name = "ind_ben_draft_created", value = ["isDraft", "createdDate"]),
        // The pull resolves a server record to a beneficiary by benRegId. The primary key is
        // beneficiaryId, so without this every such lookup was a full scan of this table -
        // 9,987 wide rows on a camp device - and the pull does one per incoming record across
        // eleven call sites in TBRepo, VitalRepo and BenRepo.
        Index(name = "ind_ben_reg_id", value = ["benRegId"])
    ]

)

data class BenRegCache(

    @ColumnInfo(index = true)
    var householdId: Long? = null,

    var beneficiaryId: Long,

    var isDeath: Boolean,
    var isDeathValue: String? = null,
    var dateOfDeath: String? = null,
    var timeOfDeath: String? = null,
    var reasonOfDeath: String? = null,
    var reasonOfDeathId: Int,
    var placeOfDeath: String? = null,
    var placeOfDeathId: Int,
    var otherPlaceOfDeath: String? = null,

    var isAvailableForCamp: Boolean = true,
    var reasonForNotAttendingCamp: String? = null,
    var otherReasonForNotAttendingCamp: String? = null,
    var screeningStatus: ScreeningStatus = ScreeningStatus.UNSCREENED,
    var symptomsScreenedDate: Long? = null,
    var chestXrayDoneDate: Long? = null,
    var trunatTestDoneDate: Long? = null,


    var benRegId: Long = 0,

    @ColumnInfo(index = true)
    var ashaId: Int,

    var isKid: Boolean,

    var isAdult: Boolean,

    var userImage: String? = null,

//    @Suppress("ArrayInDataClass")
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    var userImageBlob: ByteArray? = null,

    var regDate: Long = 0,

    var firstName: String? = null,

    var lastName: String? = null,

    var gender: Gender? = null,

    var genderId: Int = 0,

    var dob: Long = 0,

    var age: Int = 0,

    var ageUnit: AgeUnit? = null,

    var ageUnitId: Int = 0,

    var fatherName: String? = null,

    var motherName: String? = null,

    var familyHeadRelation: String? = null,

    var familyHeadRelationPosition: Int = 0,

    var familyHeadRelationOther: String? = null,

    var mobileNoOfRelation: String? = null,

    var mobileNoOfRelationId: Int = 0,

    var tempMobileNoOfRelationId: Int = 0,

    var mobileOthers: String? = null,

    var contactNumber: Long? = null,

    var literacy: String? = null,

    var literacyId: Int = 0,

    var community: String? = null,

    var communityId: Int = 0,

    var religion: String? = null,

    var religionId: Int = 0,

    var religionOthers: String? = null,

    var rchId: String? = null,

    var occupation: String? = null,

//    var registrationType: TypeOfList? = null,

    var latitude: Double = 0.0,

    var longitude: Double = 0.0,

    // GPS location captured at registration time (standalone beneficiary only)
    var gpsLatitude: Double? = null,
    var gpsLongitude: Double? = null,
    var digipin: String? = null,
    var gpsTimestamp: String? = null,
    @ColumnInfo(defaultValue = "0") var isGpsUnavailable: Boolean = false,
    var gpsUnavailableReason: String? = null,

    ///////////////////////////Bank details Start///////////////////////////
    var hasAadhar: Boolean? = false,

    var hasAadharId: Int = 0,

    var aadharNum: String? = null,

    var aadharNumId: Int = 0,

    var bankAccountId: Int = 0,

    var bankAccount: String? = null,

    var nameOfBank: String? = null,

    var nameOfBranch: String? = null,

    var ifscCode: String? = null,

    ///////////////////////////Bank details End///////////////////////////
    var needOpCare: String? = null,

    var needOpCareId: Int = 0,

    var ncdPriority: Int = 0,

    var cbacAvailable: Boolean = false,

    var guidelineId: String? = null,

    var isHrpStatus: Boolean = false,

    var immunizationStatus: Boolean = false,

    var hrpIdentificationDate: String? = null,

    var hrpLastVisitDate: String? = null,

    var suspectedNcd: String? = null,

    var suspectedNcdDiseases: String? = null,

    var suspectedTb: String? = null,

    var confirmed_Ncd: String? = null,

    var confirmedHrp: String? = null,

    var suspectedHrp: String? = null,

    var confirmedTb: String? = null,

    var confirmedNcdDiseases: String? = null,

    var diagnosisStatus: String? = null,


    /*
    5 Skipped:
        Aadhar, lastHrpVisitDate, marriageDate ( 2 copies)
        vanId and serviceMap ID, ( Can get from Foreign key)
     */


    @Embedded(prefix = "kid_")
    var kidDetails: BenRegKid? = null,

    @Embedded(prefix = "gen_")
    var genDetails: BenRegGen? = null,

    @Embedded(prefix = "loc_")
    var locationRecord: LocationRecord,

    @Embedded(prefix = "abha_")
    var healthIdDetails: BenHealthIdDetails? = null,

    var processed: String? = null,

    var serverUpdatedStatus: Int = 0,

    var createdBy: String? = null,

    var createdDate: Long? = null,

    var updatedBy: String? = null,

    var updatedDate: Long? = null,

    var serverUpdatedDate: Long? = null,

    var syncState: SyncState,

    var isDraft: Boolean,

    @ColumnInfo(name = "isConsent", defaultValue = "0")
    var isConsent: Boolean = false ,

    var isNewAbha: Boolean = false,
    var isSpouseAdded:  Boolean = false,
    var isChildrenAdded: Boolean = false,
    var isMarried: Boolean = false,
    var doYouHavechildren: Boolean = false,
    var noOfChildren: Int = 0,
    var noOfAliveChildren: Int = 0,
    var isDeactivate: Boolean =false,

    var economicStatus: String? = null,
    var economicStatusId: Int? = 0,
    var residentialArea: String? = null,
    var residentialAreaId: Int? = 0,
    var otherResidentialArea: String? = null,

    var personFrom: String? = null,
    var personFromId: Int? = null,
    var typeOfCaseFinding: String? = null,
    var typeOfCaseFindingId: Int? = null,
    var mobileNumberAvailable: Boolean? = null,
    var address: String? = null,
    var pinCode: String? = null,
    var height: Double? = null,
    var weight: Double? = null,
    var bmi: Double? = null,
    var temperature: Double? = null,
    var nikshayId: String? = null,
    var isNonHH: Boolean = false,
    var placeOfCurrentLiving: Int? = null,
    var otherPlaceOfCurrentLiving: String? = null,
    var institutionName: String? = null

)  : FormDataModel {

    fun asNetworkPostModel(context: Context, user: User, household: HouseholdCache? = null): BenPost {
        return BenPost(
            householdId = householdId?.toString(),
            benRegId = benRegId,
            countyid = locationRecord.country.id,
            processed = processed,
            providerServiceMapID = user.serviceMapId,
            vanID = user.vanId,
            aadhaNo = aadharNum ?: "",
            aadha_no = when (hasAadhar) {
                true -> "Yes"
                false -> "No"
                else -> "null"
            },
            aadha_noId = hasAadharId,
            age = age,
            ageAtMarriage = genDetails?.ageAtMarriage ?: 0,
            age_unit = when (ageUnit) {
                AgeUnit.YEARS -> "Year(s)"
                AgeUnit.MONTHS -> "Month(s)"
                AgeUnit.DAYS -> "Day(s)"
                else -> throw IllegalStateException("age_unit enum invalid data!")
            },
            age_unitId = ageUnit!!.ordinal,
            ashaId = ashaId,
            benId = beneficiaryId,
            registrationDate = getDateTimeStringFromLong(regDate)!!,
            marriageDate = getDateTimeStringFromLong(genDetails?.marriageDate),
            mobileNoOfRelation = mobileNoOfRelation,
            mobileNoOfRelationId = mobileNoOfRelationId,
            mobileOthers = mobileOthers ?: "",
            literacy = literacy,
            literacyId = literacyId,
            religionOthers = religionOthers ?: "",
            rchId = rchId ?: "",
            registrationType = if (kidDetails == null) "General Beneficiary" else "NewBorn",
            /*when (registrationType) {
                TypeOfList.INFANT,
                TypeOfList.CHILD,
                TypeOfList.ADOLESCENT -> "NewBorn"
                TypeOfList.GENERAL,
                TypeOfList.ELIGIBLE_COUPLE,
                TypeOfList.ANTENATAL_MOTHER,
                TypeOfList.DELIVERY_STAGE,
                TypeOfList.POSTNATAL_MOTHER,
                TypeOfList.MENOPAUSE,
                TypeOfList.TEENAGER -> "General Beneficiary"
                else -> "Other"
            }*/
            needOpCare = needOpCare ?: "null",
            needOpCareId = needOpCareId,
//            menstrualStatusId = 0,
//            regularityofMenstrualCycleId = 0,
//            lengthofMenstrualCycleId =  0,
//            menstrualBFDId = 0,
//            menstrualProblemId = 0,
            lastMenstrualPeriod = getDateTimeStringFromLong(genDetails?.lastMenstrualPeriod),
            /**
             * part of reproductive status id mapping on @since Aug 7
             */
            reproductiveStatusId = genDetails?.reproductiveStatusId?.let {
                when (it) {
                    0 -> 0
                    1 -> 1
                    2 -> 2
                    3 -> 3
                    4 -> 4
                    5->  5
                    else -> 6
                }
            } ?: 0,

            reproductiveStatus = genDetails?.reproductiveStatus.toString(),
//            reproductiveStatus = genDetails?.reproductiveStatusId?.let {
//                when (it) {
//                    0 -> ""
//                    1 -> "Eligible Couple"
//                    2 -> "Antenatal Mother"
//                    3 -> "Postnatal Mother-Lactating Mother"
//                    4 -> "Menopause Stage"
//                    5 -> "Permanently Sterilised"
//                    else -> "Teenager"
//                }
//            } ?: "",
//            noOfDaysForDelivery = if (genDetails?.reproductiveStatusId ==2) getNumDaysForDeliveryFromLastMenstrualPeriod(
//                genDetails?.lastMenstrualPeriod
//            ) else null,
//            formStatus = null,
//            formType = null,
//            childRegisteredAWCID = kidDetails?.childRegisteredAWCId ?: 0,
            childRegisteredSchoolID = kidDetails?.childRegisteredSchoolId ?: 0,
            typeOfSchoolId = kidDetails?.typeOfSchoolId ?: 0,
            childRegisteredSchool = kidDetails?.childRegisteredSchool,
            typeofSchool = kidDetails?.typeOfSchool,
//            previousLiveBirth = genDetails?.numPreviousLiveBirth?.toString() ?: "",
//            lastDeliveryConductedID = genDetails?.lastDeliveryConductedId ?: 0,
//            whoConductedDeliveryID = genDetails?.whoConductedDeliveryId ?: 0,
            familyHeadRelation = familyHeadRelation ?: "Other",
            familyHeadRelationPosition = familyHeadRelationPosition,
            firstName = firstName ?: "",
            lastName = lastName ?: "",
            fatherName = fatherName ?: "",
            motherName = motherName ?: "",
            spouseName = genDetails?.spouseName ?: "",
//            whoConductedDelivery = genDetails?.whoConductedDelivery,
//            lastDeliveryConducted = genDetails?.lastDeliveryConducted,
            facilitySelection = locationRecord.block.name.takeIf { it.isNotBlank() } ?: "",
            personFrom = personFrom,
            personFromId = personFromId,
            typeOfCaseFinding = typeOfCaseFinding,
            typeOfCaseFindingId = typeOfCaseFindingId,
            mobileNumberAvailable = mobileNumberAvailable,
            address = address,
            height = height,
            weight = weight,
            bmi = bmi,
            temperature = temperature,
            serverUpdatedStatus = serverUpdatedStatus,
            createdBy = createdBy!!,
            createdDate = getDateTimeStringFromLong(createdDate!!)!!,
            ncdPriority = ncdPriority,
            guidelineId = guidelineId ?: "0",
            villageName = locationRecord.village.name,
            currSubDistrictId = locationRecord.block.id,
            villageId = locationRecord.village.id,
//            expectedDateOfDelivery = getDateTimeStringFromLong(genDetails?.expectedDateOfDelivery),
            isHrpStatus = isHrpStatus,
//            menstrualStatus = genDetails?.menstrualStatus,
            dateMarriage = genDetails?.marriageDate?.let { getDateTimeStringFromLong(it) },
//            deliveryDate = genDetails?.deliveryDate,
//            suspected_hrp = genDetails?.hrpSuspected.toString(),
//            suspected_ncd = suspectedNcd,
//            suspected_tb = suspectedTb,
//            suspected_ncd_diseases = suspectedNcdDiseases,
//            confirmed_ncd = confirmed_Ncd,
//            confirmed_hrp = confirmedHrp,
//            confirmed_tb = confirmedTb,
//            confirmed_ncd_diseases = confirmedNcdDiseases,
//            diagnosis_status = diagnosisStatus,
//            nishchayPregnancyStatus = nishchayPregnancyStatus ?: "select",
//            nishchayPregnancyStatusPosition = nishchayPregnancyStatusPosition,
//            nishchayDeliveryStatus = nishchayDeliveryStatus ?: "select",
//            nishchayDeliveryStatusPosition = nishchayDeliveryStatusPosition,
//            nayiPahalDeliveryStatusPosition = nayiPahalDeliveryStatusPosition,
//            isImmunizationStatus = immunizationStatus,
            userImage = ImageUtils.getEncodedStringForBenImage(
                context,
                beneficiaryId
            ) ?: "",// Base64.encodeToString(userImageBlob, Base64.DEFAULT),
            benDemographics = BenDemographics(
                communityID = communityId.takeIf { it > 0 },
                communityName = community ?: "",
                religionID = religionId.takeIf { it > 0 },
                religionName = religion ?: "",
                countryID = 1,
                countryName = "India",
                stateID = locationRecord.state.id,
                stateName = locationRecord.state.name,
                districtID = locationRecord.district.id,
                districtName = locationRecord.district.name,
                blockID = locationRecord.block.id,
                tuId = null,
                tuName = null,
                healthFacilityId = null,
                healthFacilityName = null,
                districtBranchID = locationRecord.village.id,
                districtBranchName = locationRecord.village.name,
//            zoneID = user.zoneId,
//            zoneName = user.zoneName,
//            parkingPlaceName = user.parkingPlaceName,
//            parkingPlaceID = user.parkingPlaceId,
//            servicePointID = user.servicePointId.toString(),
//            servicePointName = user.servicePointName,
                address = null,
                addressLine1 = address ?: "",
                addressLine2 = "",
                addressLine3 = "",
                occupation = occupation ?: "unknown",
                pinCode = pinCode,
                economicStatus = economicStatus,
                economicStatusId = economicStatusId,
                residentialArea = residentialArea,
                residentialAreaId = residentialAreaId,
                otherResidentialArea = otherResidentialArea,
                latitude = gpsLatitude ?: household?.gpsLatitude ?: latitude,
                longitude = gpsLongitude ?: household?.gpsLongitude ?: longitude,
                digipin = digipin ?: household?.digipin,
                gpsTimestamp = (gpsTimestamp ?: household?.gpsTimestamp).toGpsTimestampLong(),
                isGpsUnavailable = if (gpsLatitude != null || household?.gpsLatitude != null || gpsLongitude != null || household?.gpsLongitude != null) false else (isGpsUnavailable || (household?.isGpsUnavailable ?: false)),
                gpsUnavailableReason = gpsUnavailableReason ?: household?.gpsUnavailableReason,
                createdBy = user.userName,
            ),
            benPhoneMaps = arrayOf(
                BenPhoneMaps(
                    phoneNo = contactNumber?.toString() ?: "0",
                    createdBy = user.userName,
                )
            ),
            dob = getDateStringFromLong(dob) ?: "",
            gender = gender.toString(),
            genderId = genderId,
            maritalStatusID = if (isKid) null else genDetails?.maritalStatusId?.toString() ?: null,
            maritalStatusName = if (isKid) null else genDetails?.maritalStatus ?: null,
            isDeath = isDeath,
            isDeathValue = isDeathValue ?: "",
            dateOfDeath = dateOfDeath ?:"",
            placeOfDeath = placeOfDeath ?: "",
            timeOfDeath = timeOfDeath ?: "",
            reasonOfDeath = reasonOfDeath ?: "",
            reasonOfDeathId = reasonOfDeathId ?: -1,
            placeOfDeathId = placeOfDeathId ?: -1,
            otherPlaceOfDeath = otherPlaceOfDeath?: "",
            noofAlivechildren = noOfAliveChildren,
            noOfchildren = noOfChildren,
            isMarried = isMarried,
            isChildrenAdded = isChildrenAdded,
            doYouHavechildren = doYouHavechildren,
            isSpouseAdded = isSpouseAdded,
            isDeactivate = isDeactivate,
            isNonHH = isNonHH,
            placeOfCurrentLiving = getPlaceOfCurrentLivingCode(placeOfCurrentLiving),
            otherPlaceOfCurrentLiving = otherPlaceOfCurrentLiving,
            institutionName = institutionName
        )
    }

    private fun getNumDaysForDeliveryFromLastMenstrualPeriod(lastMenstrualPeriod: Long?): Int? {
        if (lastMenstrualPeriod == null)
            return null
        val millisCurrent = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastMenstrualPeriod
        cal.add(Calendar.WEEK_OF_YEAR, 40)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis - millisCurrent).toInt()
    }

    fun asKidNetworkModel(user: User): BenRegKidNetwork {
        return BenRegKidNetwork(
            benficieryid = beneficiaryId,
            childName = firstName,
            birthPlace = kidDetails!!.birthPlace,
            birthPlaceid = kidDetails!!.birthPlaceId,
            facilityName = kidDetails!!.facilityName,
            facilityid = kidDetails!!.facilityId,
            ashaid = ashaId,
            facilityOther = kidDetails!!.facilityOther,
            placeName = kidDetails!!.placeName,
            conductedDelivery = kidDetails!!.conductedDelivery,
            conductedDeliveryid = kidDetails!!.conductedDeliveryId,
            conductedDeliveryOther = kidDetails!!.conductedDeliveryOther,
            deliveryType = kidDetails!!.deliveryType,
            deliveryTypeid = kidDetails!!.deliveryTypeId,
            deliveryTypeOther = kidDetails!!.deliveryTypeOther,
            complications = kidDetails!!.complications,
            complicationsid = kidDetails!!.complicationsId,
            complicationsOther = kidDetails!!.complicationsOther,
            term = kidDetails!!.term,
            termid = kidDetails!!.termId,
            gestationalAge = kidDetails!!.gestationalAge,
            gestationalAgeid = kidDetails!!.gestationalAgeId,
            corticosteroidGivenMother = kidDetails!!.corticosteroidGivenMother,
            corticosteroidGivenMotherid = kidDetails!!.corticosteroidGivenMotherId,
            criedImmediately = kidDetails!!.criedImmediately,
            criedImmediatelyid = kidDetails!!.criedImmediatelyId,
            birthDefects = kidDetails!!.birthDefects,
            birthDefectsid = kidDetails!!.birthDefectsId,
            birthDefectsOthers = kidDetails!!.birthDefectsOthers,
            heightAtBirth = kidDetails!!.heightAtBirth.toInt(),
            weightAtBirth = kidDetails!!.weightAtBirth.toFloat(),
            feedingStarted = kidDetails!!.feedingStarted,
            feedingStartedid = kidDetails!!.feedingStartedId,
            birthDosage = kidDetails!!.birthDosage,
            birthDosageid = kidDetails!!.birthDosageId,
            birthBCG = kidDetails!!.birthBCG,
            birthOPV = kidDetails!!.birthOPV,
            birthHepB = kidDetails!!.birthHepB,
            motherBenId = kidDetails!!.motherBenId,
            motherName = kidDetails!!.childMotherName,
            motherposition = kidDetails!!.motherPosition,
            createdBy = createdBy,
            createdDate = getDateTimeStringFromLong(createdDate),
            updatedBy = updatedBy,
            updatedDate = getDateTimeStringFromLong(updatedDate),
            Processed = processed,
            serverUpdatedStatus = serverUpdatedStatus,
            VanID = 4,
            ProviderServiceMapID = user.serviceMapId,
            Countyid = locationRecord.country.id,
            stateid = locationRecord.state.id,
            districtid = locationRecord.district.id,
            villageid = locationRecord.village.id,
            isConsent = isConsent,

            )
    }
}

fun getDateTimeStringFromLong(dateLong: Long?): String? {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
    dateLong?.let {
        val dateString = dateFormat.format(dateLong)
        val timeString = timeFormat.format(dateLong)
        return "${dateString}T${timeString}.000Z"
    } ?: run {
        return null
    }

}

fun getPlaceOfCurrentLivingCode(index: Int?): String? {
    return when (index) {
        1 -> "FOOTPATH"
        2 -> "RAILWAY_PLATFORM"
        3 -> "BUS_STATION"
        4 -> "UNDER_TREE"
        5 -> "TEMPLE"
        6 -> "MOSQUE"
        7 -> "CHURCH"
        8 -> "OTHER_PRAYING_PLACE"
        9 -> "EDUCATIONAL_INSTITUTION"
        10 -> "EKALAVYA_SCHOOL"
        11 -> "REHABILITATION_CENTRE"
        12 -> "ORPHANAGE"
        13 -> "OLD_AGE_HOME"
        14 -> "PRIVATE_HOSTEL"
        15 -> "GOVT_HOSTEL"
        16 -> "NGO_HOSTEL"
        17 -> "OTHER"
        else -> null
    }
}

fun getPlaceOfCurrentLivingIndex(code: String?): Int? {
    return when (code?.uppercase()) {
        "FOOTPATH" -> 1
        "RAILWAY_PLATFORM" -> 2
        "BUS_STATION" -> 3
        "UNDER_TREE" -> 4
        "TEMPLE" -> 5
        "MOSQUE" -> 6
        "CHURCH" -> 7
        "OTHER_PRAYING_PLACE" -> 8
        "EDUCATIONAL_INSTITUTION" -> 9
        "EKALAVYA_SCHOOL" -> 10
        "REHABILITATION_CENTRE" -> 11
        "ORPHANAGE" -> 12
        "OLD_AGE_HOME" -> 13
        "PRIVATE_HOSTEL" -> 14
        "GOVT_HOSTEL" -> 15
        "NGO_HOSTEL" -> 16
        "OTHER" -> 17
        else -> null
    }
}
