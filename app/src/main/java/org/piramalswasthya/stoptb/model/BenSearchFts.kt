package org.piramalswasthya.stoptb.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Search index for the beneficiary lists: one row per beneficiary (rowid = beneficiaryId) with
 * the fields the search box looks in. Kept current by triggers on BENEFICIARY and HOUSEHOLD
 * ([org.piramalswasthya.stoptb.database.room.BenSearchSql]); the app never writes it directly.
 * Matches whole words or the start of a word, case-insensitively.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "BEN_SEARCH_FTS")
data class BenSearchFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    /** First and last name. */
    val name: String?,
    val phone: String?,
    /** Last 4 to 9 digits of the phone, so a search by the end of the number finds it. */
    @ColumnInfo(name = "phoneend") val phoneEnd: String?,
    /** ABHA number without dashes. */
    val abha: String?,
    /** Household's family head name. */
    val head: String?,
    val spouse: String?,
    val father: String?,
    val benId: String?,
    val hhId: String?,
    val rch: String?,
)
