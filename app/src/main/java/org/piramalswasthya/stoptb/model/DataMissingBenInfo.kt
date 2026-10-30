package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

const val MISSING_DATA_REASON_SEPARATOR = "|||"

@Entity(tableName = "DATA_MISSING_BEN_INFO")
data class DataMissingBenInfo(
    @PrimaryKey
    val beneficiaryId: Long,
    val status: MissingDataInfoStatus = MissingDataInfoStatus.MISSING,
    val reason: String = ""
)

class MissingDataInfoStatusConverter {

    @TypeConverter
    fun fromStatus(status: MissingDataInfoStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): MissingDataInfoStatus {
        return MissingDataInfoStatus.valueOf(value)
    }
}

enum class MissingDataInfoStatus{
    MISSING,
    UPDATED,
    SYNCED
}
