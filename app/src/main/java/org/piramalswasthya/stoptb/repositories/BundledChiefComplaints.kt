package org.piramalswasthya.stoptb.repositories

import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache

/** Product-supplied static IDs, 2026-10-29. Never write these into the API cache. */
internal object BundledChiefComplaints {
    val items: List<ChiefComplaintMasterCache> = listOf(
        ChiefComplaintMasterCache(1, "Fever"),
        ChiefComplaintMasterCache(2, "Headache"),
        ChiefComplaintMasterCache(3, "Body aches"),
        ChiefComplaintMasterCache(4, "Runny nose"),
        ChiefComplaintMasterCache(5, "Sneezing"),
        ChiefComplaintMasterCache(6, "Nasal congestion"),
        ChiefComplaintMasterCache(7, "Itching"),
        ChiefComplaintMasterCache(8, "Sore throat"),
        ChiefComplaintMasterCache(9, "Cough"),
        ChiefComplaintMasterCache(10, "Loose stools"),
        ChiefComplaintMasterCache(11, "Vomiting"),
        ChiefComplaintMasterCache(12, "Abdominal pain"),
        ChiefComplaintMasterCache(13, "Epigastric pain"),
        ChiefComplaintMasterCache(14, "Burning sensation in chest"),
        ChiefComplaintMasterCache(15, "Indigestion"),
        ChiefComplaintMasterCache(16, "Bloating"),
        ChiefComplaintMasterCache(17, "Nausea"),
        ChiefComplaintMasterCache(18, "Weakness/fatigue"),
        ChiefComplaintMasterCache(19, "Loss of appetite"),
        ChiefComplaintMasterCache(20, "Regurgitation of food"),
        ChiefComplaintMasterCache(21, "Others")
    )

    fun resolveIds(names: List<String>): List<Int>? = names.map { name ->
        items.singleOrNull { it.chiefComplaint.equals(name.trim(), ignoreCase = true) }
            ?.chiefComplaintId ?: return null
    }
}
