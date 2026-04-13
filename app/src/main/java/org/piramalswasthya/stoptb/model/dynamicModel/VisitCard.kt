package org.piramalswasthya.stoptb.model.dynamicModel

data class VisitCard(
    val visitDay: String,
    val visitDate: String,
    val isCompleted: Boolean,
    val isEditable: Boolean,
    val isBabyDeath: Boolean
)
