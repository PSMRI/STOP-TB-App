package org.piramalswasthya.stoptb.database.converters

import androidx.room.TypeConverter
import org.piramalswasthya.stoptb.model.Gender

object GenderConverter {

    @TypeConverter
    fun fromGender(gender: Gender?): String? = gender?.name

    @TypeConverter
    fun toGender(value: String?): Gender? {
        if (value.isNullOrBlank()) return null
        return try {
            Gender.valueOf(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
