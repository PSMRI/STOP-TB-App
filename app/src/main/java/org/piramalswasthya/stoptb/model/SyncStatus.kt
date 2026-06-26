package org.piramalswasthya.stoptb.model

import org.piramalswasthya.stoptb.database.room.SyncState

data class SyncStatusCache(

    val id: Int, val name: String, val syncState: SyncState, val count: Int

)


fun List<SyncStatusCache>.asDomainModel(
    localNames: Array<String>,
    englishNames: Array<String>
): List<SyncStatusDomain> {
    val activeCategories = listOf(
        "Beneficiary",
        "TB Screening",
        "TB Suspected",
        "Anthropometric",
        "General Examination",
        "OPD",
        "Diagnosis",
        "Counselling"
    )
    val grouped = groupBy { it.name }
    return activeCategories.map { englishName ->
        val cacheItems = grouped[englishName] ?: emptyList()
        val localName = if (englishNames.contains(englishName)) {
            val idx = englishNames.indexOf(englishName)
            if (idx >= 0 && idx < localNames.size) localNames[idx] else englishName
        } else englishName

        SyncStatusDomain(
            name = localName,
            synced = cacheItems.firstOrNull { it.syncState == SyncState.SYNCED }?.count ?: 0,
            notSynced = cacheItems.firstOrNull { it.syncState == SyncState.UNSYNCED }?.count ?: 0,
            syncing = cacheItems.firstOrNull { it.syncState == SyncState.SYNCING }?.count ?: 0
        )
    }
}

data class SyncStatusDomain(
    val name: String,
    val synced: Int,
    val notSynced: Int,
    val syncing: Int,
    val totalCount: Int = synced + notSynced + syncing
)