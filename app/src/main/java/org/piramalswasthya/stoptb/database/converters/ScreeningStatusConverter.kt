package org.piramalswasthya.stoptb.database.converters


import androidx.room.TypeConverter
import org.piramalswasthya.stoptb.model.ScreeningStatus

class ScreeningStatusConverter {
    @TypeConverter
    fun fromScreeningStatus(value: ScreeningStatus): String = value.name

    @TypeConverter
    fun toScreeningStatus(value: String): ScreeningStatus =
        runCatching { ScreeningStatus.valueOf(value) }.getOrDefault(ScreeningStatus.UNSCREENED)
}