package org.piramalswasthya.stoptb.model

import androidx.navigation.NavDirections
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState

data class Icon(
    val icon: Int,
    val title: String,
    val subtitle: String? = null,
    val count: Flow<Int>?,
    val navAction: NavDirections,
    var colorPrimary: Boolean = true,
    val allowRedBorder: Boolean = false
)





data class ImmunizationIcon(
    val benId: Long,
    val hhId: Long,
    val title: String,
    val count: Int,
    val maxCount: Int = 5,

//    val typeOfList: TypeOfList
)

data class HbncIcon(
    val hhId: Long,
    val benId: Long,
    val count: Int,
    val isFilled: Boolean,
    val syncState: SyncState?,
    val title: String = "Day $count",
    val destination: NavDirections
)

data class HbycIcon(
    val hhId: Long,
    val benId: Long,
    val count: Int,
    val isFilled: Boolean,
    val syncState: SyncState?,
    val title: String = "Month $count",
    val destination: NavDirections
)
