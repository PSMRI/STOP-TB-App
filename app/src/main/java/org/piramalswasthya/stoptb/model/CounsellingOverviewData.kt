package org.piramalswasthya.stoptb.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CounsellingOverviewData(
    val benId: Long,
    val patientName: String,
    val nikshayId: String,
    val counsellingDate: String,
    val counsellingOfficer: String,
    val regDate: Long,
    val beneficiaryId: String,
    val ageGender: String,
    val diagnosis: String,
    val currentStep: Int = 0,
    val completedSteps: Int = 0,
    val status: String = "DRAFT",
    val preSubmitSubmitted: Boolean = false
) : Parcelable