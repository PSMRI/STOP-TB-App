package org.piramalswasthya.stoptb.model.dynamicModel

data class HBNCVisitRequest(
    val fromDate: String,
    val toDate: String,
    val pageNo: Int,
    val ashaId: Int,
    val userName: String
)
