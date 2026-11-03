package org.piramalswasthya.stoptb.model

import org.piramalswasthya.stoptb.database.room.SyncState

/**
 * One row's id plus its sync state.
 *
 * The list screens need three sets per table - all ids, the unsynced ones and the syncing ones -
 * which used to be three separate Room queries, three Flows and three collectors per table. Every
 * write to the table re-ran all three. Reading id+syncState once and splitting it in memory gives
 * the same three sets from a single query.
 */
data class BenIdSyncState(
    val benId: Long,
    val syncState: SyncState?
)
