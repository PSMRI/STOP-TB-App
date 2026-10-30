package org.piramalswasthya.stoptb.database.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.DataMissingBenInfo
import org.piramalswasthya.stoptb.model.MISSING_DATA_REASON_SEPARATOR
import org.piramalswasthya.stoptb.model.MissingDataInfoStatus
import org.piramalswasthya.stoptb.model.ScreeningStatus
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben.MissingDataBenItem

@Dao
interface DataMissingBenInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: DataMissingBenInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<DataMissingBenInfo>)

    @Update
    suspend fun update(data: DataMissingBenInfo)

    @Transaction
    @Query(
        """
        SELECT
            d.beneficiaryId AS id,
            d.reason AS reasons,

            b.beneficiaryId AS benId,
            b.householdId AS hhId,

            b.isDeath AS isDeath,
            b.isDeathValue AS isDeathValue,
            b.dateOfDeath AS dateOfDeath,
            b.timeOfDeath AS timeOfDeath,
            b.reasonOfDeath AS reasonOfDeath,
            b.reasonOfDeathId AS reasonOfDeathId,
            b.placeOfDeath AS placeOfDeath,
            b.placeOfDeathId AS placeOfDeathId,
            b.otherPlaceOfDeath AS otherPlaceOfDeath,

            b.gen_reproductiveStatusId AS reproductiveStatusId,

            CAST(b.regDate AS TEXT) AS regDate,

            b.firstName AS benName,
            b.lastName AS benSurname,
            b.gender,
            b.dob,

            b.familyHeadRelationPosition AS relToHeadId,
            b.contactNumber AS mobileNo,

            b.abha_healthIdNumber AS abhaId,
            b.isNewAbha,

            b.fatherName,
            b.motherName,

            IFNULL(
                h.fam_familyHeadName,
                ''
            ) AS familyHeadName,

            b.gen_spouseName AS spouseName,

            b.rchId,
            b.nikshayId,

            b.isHrpStatus AS hrpStatus,
            b.syncState,

            b.isConsent,
            b.isSpouseAdded,
            b.isChildrenAdded,
            b.isMarried,

            b.doYouHavechildren,
            b.noOfChildren,
            b.noOfAliveChildren,

            b.isDeactivate,

            b.isNonHH,
            b.isAvailableForCamp,

            b.reasonForNotAttendingCamp,
            b.otherReasonForNotAttendingCamp,

            b.screeningStatus,

            b.symptomsScreenedDate,
            b.chestXrayDoneDate,
            b.trunatTestDoneDate,

            b.placeOfCurrentLiving,
            b.otherPlaceOfCurrentLiving,
            b.institutionName

        FROM DATA_MISSING_BEN_INFO d

        INNER JOIN BENEFICIARY b
            ON d.beneficiaryId = b.beneficiaryId

        LEFT JOIN HOUSEHOLD h
            ON b.householdId = h.householdId

        WHERE d.status = 'MISSING'

        AND b.loc_village_id IN (:villageIds)

        AND (
            :searchQuery = ''
            OR b.firstName LIKE '%' || :searchQuery || '%'
        )

        ORDER BY b.updatedDate DESC
        """
    )
    fun getMissingBeneficiaries(
        searchQuery: String,
        villageIds: List<Int>
    ): PagingSource<Int, MissingDataBenQuery>

    @Query("""
    UPDATE DATA_MISSING_BEN_INFO
    SET status = :status
    WHERE beneficiaryId = :beneficiaryId
""")
    suspend fun updateStatus(
        beneficiaryId: Long,
        status: MissingDataInfoStatus
    ): Int

    @Query("""
    SELECT COUNT(*)
    FROM DATA_MISSING_BEN_INFO d
    INNER JOIN BENEFICIARY b ON d.beneficiaryId = b.beneficiaryId
    WHERE d.status = 'MISSING'
    AND b.loc_village_id = :villageId
""")
    fun getDataCount(villageId: Int): Flow<Int>
}

data class MissingDataBenQuery(
    val id: Long,
    val reasons: String?,

    val benId: Long,
    val hhId: Long,

    val isDeath: Boolean,
    val isDeathValue: String?,
    val dateOfDeath: String?,
    val timeOfDeath: String?,
    val reasonOfDeath: String?,
    val reasonOfDeathId: Int?,
    val placeOfDeath: String?,
    val placeOfDeathId: Int?,
    val otherPlaceOfDeath: String?,

    val reproductiveStatusId: Int,

    val regDate: String,

    val benName: String,
    val benSurname: String?,
    val gender: String,
    val dob: Long,

    val relToHeadId: Int,
    val mobileNo: String,

    val abhaId: String?,
    val isNewAbha: Boolean,

    val fatherName: String?,
    val motherName: String?,

    val familyHeadName: String,

    val spouseName: String?,
    val rchId: String?,
    val nikshayId: String?,

    val hrpStatus: Boolean,

    val syncState: SyncState?,

    val isConsent: Boolean,
    val isSpouseAdded: Boolean,
    val isChildrenAdded: Boolean,
    val isMarried: Boolean,

    val doYouHavechildren: Boolean,
    val noOfChildren: Int,
    val noOfAliveChildren: Int,

    val isDeactivate: Boolean,
    val isNonHH: Boolean,

    val isAvailableForCamp: Boolean,
    val reasonForNotAttendingCamp: String?,
    val otherReasonForNotAttendingCamp: String?,

    val screeningStatus: ScreeningStatus,

    val symptomsScreenedDate: Long?,
    val chestXrayDoneDate: Long?,
    val trunatTestDoneDate: Long?,

    val placeOfCurrentLiving: Int?,
    val otherPlaceOfCurrentLiving: String?,
    val institutionName: String?
) {
    fun toDomain(): MissingDataBenItem {
        return MissingDataBenItem(
            id = id,
            reasons = reasons
                ?.split(MISSING_DATA_REASON_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            benBasicDomain = BenBasicDomain(
                benId = benId,
                hhId = hhId,

                isDeath = isDeath,
                isDeathValue = isDeathValue,
                dateOfDeath = dateOfDeath,
                timeOfDeath = timeOfDeath,
                reasonOfDeath = reasonOfDeath,
                reasonOfDeathId = reasonOfDeathId,
                placeOfDeath = placeOfDeath,
                placeOfDeathId = placeOfDeathId,
                otherPlaceOfDeath = otherPlaceOfDeath,

                reproductiveStatusId = reproductiveStatusId,

                regDate = regDate,
                benName = benName,
                benSurname = benSurname,
                gender = gender,
                dob = dob,

                relToHeadId = relToHeadId,
                mobileNo = mobileNo,

                abhaId = abhaId,
                isNewAbha = isNewAbha,

                fatherName = fatherName,
                motherName = motherName,

                familyHeadName = familyHeadName,
                spouseName = spouseName,

                rchId = rchId,
                nikshayId = nikshayId,

                hrpStatus = hrpStatus,
                syncState = syncState,

                isConsent = isConsent,
                isSpouseAdded = isSpouseAdded,
                isChildrenAdded = isChildrenAdded,
                isMarried = isMarried,

                doYouHavechildren = doYouHavechildren,
                noOfChildren = noOfChildren,
                noOfAliveChildren = noOfAliveChildren,

                isDeactivate = isDeactivate,
                isNonHH = isNonHH,

                isAvailableForCamp = isAvailableForCamp,
                reasonForNotAttendingCamp = reasonForNotAttendingCamp,
                otherReasonForNotAttendingCamp = otherReasonForNotAttendingCamp,

                screeningStatus = screeningStatus,
                symptomsScreenedDate = symptomsScreenedDate,
                chestXrayDoneDate = chestXrayDoneDate,
                trunatTestDoneDate = trunatTestDoneDate,

                placeOfCurrentLiving = placeOfCurrentLiving,
                otherPlaceOfCurrentLiving = otherPlaceOfCurrentLiving,
                institutionName = institutionName
            )
        )
    }
}