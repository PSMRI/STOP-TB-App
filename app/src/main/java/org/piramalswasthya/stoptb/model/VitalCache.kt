package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.piramalswasthya.stoptb.database.room.SyncState

@Entity(
    tableName = "BEN_VITALS",
    foreignKeys = [
        ForeignKey(
            entity = BenRegCache::class,
            parentColumns = ["beneficiaryId"],
            childColumns = ["benId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "ind_vitals_ben", value = ["benId"])]
)
data class VitalCache(
    @PrimaryKey
    val benId: Long,
    val benRegId: Long,
    var capturedAt: Long = System.currentTimeMillis(),
    var temperature: Double? = null,
    var pulseRate: Int? = null,
    var bpSystolic: Int? = null,
    var bpDiastolic: Int? = null,
    var respiratoryRate: Int? = null,
    var spo2: Int? = null,
    var height: Double? = null,
    var weight: Double? = null,
    var bmi: Double? = null,
    var rbs: Double? = null,
    var syncState: SyncState = SyncState.UNSYNCED,
)
