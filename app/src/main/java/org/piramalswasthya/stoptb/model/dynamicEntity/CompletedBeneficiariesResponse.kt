package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.annotations.SerializedName

data class CompletedBeneficiariesResponse(
    @SerializedName("completed")
    val completed: List<Long>,
    @SerializedName("refused")
    val refused: List<Long>
)
