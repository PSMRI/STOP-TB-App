package org.piramalswasthya.stoptb.model

/**
 * Maps a server-side `benRegId` to the local `beneficiaryId`, which is all a pull needs in order
 * to attach an incoming record to a beneficiary.
 *
 * Pull save loops used to resolve this one record at a time with
 * `SELECT * FROM BENEFICIARY WHERE benRegId = ? LIMIT 1`. `benRegId` carries no index - the
 * primary key is `beneficiaryId` - so every one of those was a full scan of the beneficiary
 * table, materialising wide rows, to read a single id.
 */
data class BenRegIdMapping(
    val benRegId: Long,
    val beneficiaryId: Long
)
