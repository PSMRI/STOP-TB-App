package org.piramalswasthya.stoptb.model.contactTracing

data class ContactTracingStatus(
    val isCommunitySubmitted: Boolean = false,
    val isOccupationalSubmitted: Boolean = false
)
