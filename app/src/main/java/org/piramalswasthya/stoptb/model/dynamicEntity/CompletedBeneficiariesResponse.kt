package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.annotations.SerializedName

data class CompletedBeneficiaryStatus(
    @SerializedName("beneficiaryId")
    val beneficiaryId: Long,
    @SerializedName("sectionsFilled")
    val sectionsFilled: Int,
    @SerializedName("totalSections")
    val totalSections: Int,
    @SerializedName("refused")
    val refused: Boolean
)
