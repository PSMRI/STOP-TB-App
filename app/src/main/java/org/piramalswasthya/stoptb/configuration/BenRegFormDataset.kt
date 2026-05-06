package org.piramalswasthya.stoptb.configuration

import android.content.Context
import android.net.Uri
import android.text.InputType
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.helpers.setToStartOfTheDay
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.model.BenBasicCache.Companion.getAgeFromDob
import org.piramalswasthya.stoptb.model.BenBasicCache.Companion.getYearsFromDate
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.BenRegGen
import org.piramalswasthya.stoptb.model.BenStatus
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.Gender.FEMALE
import org.piramalswasthya.stoptb.model.Gender.MALE
import org.piramalswasthya.stoptb.model.Gender.TRANSGENDER
import org.piramalswasthya.stoptb.model.Gender.PREFER_NOT_TO_SAY
import org.piramalswasthya.stoptb.model.InputType.DATE_PICKER
import org.piramalswasthya.stoptb.model.InputType.DROPDOWN
import org.piramalswasthya.stoptb.model.InputType.EDIT_TEXT
import org.piramalswasthya.stoptb.model.InputType.IMAGE_VIEW
import org.piramalswasthya.stoptb.model.InputType.RADIO
import org.piramalswasthya.stoptb.model.InputType.TEXT_VIEW
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel.Companion.isOtpVerified
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BenRegFormDataset(context: Context, language: Languages) : Dataset(context, language) {

    companion object {
        private fun getCurrentDateString(): String {
            val calendar = Calendar.getInstance()
            val mdFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            return mdFormat.format(calendar.time)
        }

        private fun getMinDobMillis(): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -1 * Konstants.maxAgeForGenBen)
            return cal.timeInMillis
        }
    }

    // ─────────────────────────── FORM FIELDS ───────────────────────────

    // 1. Photo
    private val pic = FormElement(
        id = 1, inputType = IMAGE_VIEW,
        title = resources.getString(R.string.nbr_image),
        subtitle = resources.getString(R.string.nbr_image_sub),
        arrayId = -1, required = false
    )

    // 2. Date of Registration
    private val dateOfReg = FormElement(
        id = 2, inputType = DATE_PICKER,
        title = resources.getString(R.string.nbr_dor),
        arrayId = -1, required = true,
        min = getMinDateOfReg(), max = System.currentTimeMillis()
    )

    // 3. Beneficiary Status (Alive/Death)
    private val beneficiaryStatus = FormElement(
        id = 50, inputType = RADIO,
        title = context.getString(R.string.beneficiary_status),
        arrayId = R.array.beneficiary_status,
        entries = resources.getStringArray(R.array.beneficiary_status),
        required = false, hasDependants = true
    )

    private val dateOfDeath = FormElement(
        id = 51, arrayId = -1, inputType = DATE_PICKER,
        title = context.getString(R.string.date_of_death),
        max = System.currentTimeMillis(), required = true
    )
    private val timeOfDeath = FormElement(
        id = 52, inputType = org.piramalswasthya.stoptb.model.InputType.TIME_PICKER,
        title = context.getString(R.string.time_of_death), required = false
    )
    private val reasonOfDeath = FormElement(
        id = 53, inputType = DROPDOWN,
        title = context.getString(R.string.reason_for_death),
        arrayId = R.array.reason_of_death_array,
        entries = resources.getStringArray(R.array.reason_of_death_array), required = true
    )
    private val placeOfDeath = FormElement(
        id = 54, inputType = DROPDOWN,
        title = context.getString(R.string.place_of_death),
        arrayId = R.array.death_place_array,
        entries = resources.getStringArray(R.array.death_place_array), required = true
    )
    private val otherPlaceOfDeath = FormElement(
        id = 55, inputType = EDIT_TEXT,
        title = context.getString(R.string.other_place_of_death),
        required = true, hasDependants = true
    )

    // 4. First Name
    private val firstName = FormElement(
        id = 3, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_nb_first_name),
        arrayId = -1, required = true, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )

    // 5. Last Name
    private val lastName = FormElement(
        id = 4, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_nb_last_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )

    // 6. Age / DOB
    private val agePopup = FormElement(
        id = 115, inputType = org.piramalswasthya.stoptb.model.InputType.AGE_PICKER,
        title = resources.getString(R.string.nbr_age),
        subtitle = resources.getString(R.string.nbr_dob),
        arrayId = -1, required = true, allCaps = true, hasSpeechToText = true,
        hasDependants = true,
        etInputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL,
        max = System.currentTimeMillis(),
        min = getMinDobMillis()
    )

    // 7. Gender
    val gender = FormElement(
        id = 9, inputType = DROPDOWN,
        title = resources.getString(R.string.nbr_gender),
        arrayId = -1,
        entries = resources.getStringArray(R.array.nbr_gender_array),
        required = true, hasDependants = true
    )

    // 8. Mobile Number
    val mobileNoOfRelation = FormElement(
        id = 12, inputType = DROPDOWN,
        title = resources.getString(R.string.whose_mobile_number),
        arrayId = R.array.nbr_mobile_no_relation_array,
        entries = resources.getStringArray(R.array.nbr_mobile_no_relation_array),
        required = true, hasDependants = true
    )
    private val otherMobileNoOfRelation = FormElement(
        id = 13, inputType = EDIT_TEXT,
        title = resources.getString(R.string.other_mobile_number_of_kid),
        arrayId = -1, required = true
    )
    private val contactNumber = FormElement(
        id = 14, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nrb_contact_number),
        arrayId = -1, required = true,
        etInputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL,
        isMobileNumber = true, etMaxLength = 10,
        max = 9999999999, min = 6000000000
    )

    // 9. Marital Status
    private val maritalStatusMale   = resources.getStringArray(R.array.nbr_marital_status_male_array)
    private val maritalStatusFemale = resources.getStringArray(R.array.nbr_marital_status_female_array)
    private val maritalStatus = FormElement(
        id = 1008, inputType = DROPDOWN,
        title = resources.getString(R.string.marital_status),
        arrayId = R.array.nbr_marital_status_male_array,
        entries = maritalStatusMale, required = false, hasDependants = true
    )
    private val husbandName = FormElement(
        id = 1009, inputType = EDIT_TEXT,
        title = resources.getString(R.string.husband_s_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )
    private val wifeName = FormElement(
        id = 1010, inputType = EDIT_TEXT,
        title = resources.getString(R.string.wife_s_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )
    private val spouseName = FormElement(
        id = 1011, inputType = EDIT_TEXT,
        title = resources.getString(R.string.spouse_s_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )
    var ageAtMarriage = FormElement(
        id = 1012, inputType = EDIT_TEXT,
        title = resources.getString(R.string.age_at_marriagee),
        etMaxLength = 2, arrayId = -1, required = true, hasDependants = true,
        etInputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL,
        min = Konstants.minAgeForGenBen.toLong(), max = Konstants.maxAgeForGenBen.toLong()
    )
    private val dateOfMarriage = FormElement(
        id = 1013, inputType = DATE_PICKER,
        title = resources.getString(R.string.date_of_marriage),
        arrayId = -1, required = true,
        max = System.currentTimeMillis(), min = getMinDobMillis()
    )

    // 10. Father Name
    private val fatherName = FormElement(
        id = 10, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_father_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )

    // 11. Mother Name
    private val motherName = FormElement(
        id = 11, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_mother_name),
        arrayId = -1, required = false, allCaps = true, hasSpeechToText = true,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )

    // 12. Community
    private val community = FormElement(
        id = 17, inputType = DROPDOWN,
        title = resources.getString(R.string.community),
        arrayId = R.array.community_array,
        entries = resources.getStringArray(R.array.community_array), required = false
    )

    // 13. Religion
    val religion = FormElement(
        id = 18, inputType = DROPDOWN,
        title = resources.getString(R.string.religion),
        arrayId = R.array.religion_array,
        entries = resources.getStringArray(R.array.religion_array),
        required = false, hasDependants = true
    )
    private val otherReligion = FormElement(
        id = 19, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_religion_other),
        arrayId = -1, required = true, allCaps = true
    )

    // 14. Economic Status (APL/BPL/Don't know) — from Household BRD
    private val economicStatus = FormElement(
        id = 1030, inputType = RADIO,
        title = resources.getString(R.string.nhhr_poverty_line),
        arrayId = R.array.nhhr_poverty_line_array,
        entries = resources.getStringArray(R.array.nhhr_poverty_line_array),
        required = false
    )

    private val residentialAreaType = FormElement(
        id = 1031,
        inputType = DROPDOWN,
        title = resources.getString(R.string.nhhr_type_residential_area),
        arrayId = R.array.nhhr_type_residential_area_array,
        entries = resources.getStringArray(R.array.nhhr_type_residential_area_array),
        required = false,
        hasDependants = true
    )
    private val otherResidentialAreaType = FormElement(
        id = 1032,
        inputType = EDIT_TEXT,
        title = resources.getString(R.string.nhhr_type_residential_area_other),
        arrayId = -1, required = true, etMaxLength = 100
    )

    // 16. Village/Hamlet (dropdown, populated from user's assigned villages)
    private val villageHamlet = FormElement(
        id = 1040, inputType = DROPDOWN,
        title = resources.getString(R.string.nbr_village),
        arrayId = -1, required = true, hasDependants = false
    )

    private val subCentre = FormElement(
        id = 1042, inputType = DROPDOWN,
        title = resources.getString(R.string.str_sub_center),
        arrayId = -1, required = true, hasDependants = false
    )

    // 17. Occupation (free text, default "unknown")
    private val occupation = FormElement(
        id = 1041, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_occupation),
        arrayId = -1, required = false,
        etInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    )

    // 18. RCH ID
    val rchId = FormElement(
        id = 23, inputType = EDIT_TEXT,
        title = resources.getString(R.string.nbr_rch_id),
        arrayId = -1, required = false,
        etInputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL,
        isMobileNumber = true, etMaxLength = 12,
        max = 999999999999, min = 100000000000
    )

    // 17. Reproductive Status (for eligible females)
    private val reproductiveStatus = FormElement(
        id = 1028, inputType = RADIO,
        title = resources.getString(R.string.reproductive_status),
        arrayId = R.array.nbr_reproductive_status_married_women_array,
        entries = resources.getStringArray(R.array.nbr_reproductive_status_married_women_array),
        required = true, hasDependants = false
    )

    // ─────────────────────────── PAGE SETUP ───────────────────────────

    /**
     * Setup page for NEW beneficiary registration
     */
    suspend fun setUpPage(ben: BenRegCache?, familyHeadPhoneNo: Long?, villageName: String? = null, villageNames: Array<String>? = null, villageEntityList: List<LocationEntity> = emptyList(), subCentreName: String? = null) {
        val list = mutableListOf(
            pic,
            dateOfReg,
            firstName,
            lastName,
            agePopup,
            gender,
            villageHamlet,
            subCentre,
            fatherName,
            motherName,
            contactNumber,
            mobileNoOfRelation,
            community,
            religion,
            economicStatus,
            residentialAreaType,
            occupation,
        )

        this.familyHeadPhoneNo = familyHeadPhoneNo?.toString()

        if (dateOfReg.value == null) dateOfReg.value = getCurrentDateString()
        contactNumber.value = familyHeadPhoneNo?.toString()
        if (mobileNoOfRelation.value.isNullOrBlank()) {
            mobileNoOfRelation.value = mobileNoOfRelation.entries?.firstOrNull()
        }
        villageNames?.let { villageHamlet.entries = it }
        villageHamlet.value = villageName ?: villageNames?.firstOrNull() ?: ""
        val resolvedSubCentre = subCentreName?.takeIf { it.isNotBlank() } ?: ""
        subCentre.entries = arrayOf(resolvedSubCentre)
        subCentre.value = resolvedSubCentre
        this.villageEntities = villageEntityList
        if (occupation.value == null) occupation.value = resources.getString(R.string.nbr_occupation_default)

        ben?.takeIf { !it.isDraft }?.let { saved ->
            // Beneficiary Status (death)
            list.add(list.indexOf(lastName) + 1, beneficiaryStatus)
            if (saved.isDeath) {
                list.add(list.indexOf(beneficiaryStatus) + 1, dateOfDeath)
                list.add(list.indexOf(dateOfDeath) + 1, timeOfDeath)
                list.add(list.indexOf(timeOfDeath) + 1, reasonOfDeath)
                list.add(list.indexOf(reasonOfDeath) + 1, placeOfDeath)
                if (placeOfDeath.entries?.indexOf(saved.placeOfDeath)?.takeIf { it >= 0 } == 8) {
                    list.add(list.indexOf(placeOfDeath) + 1, otherPlaceOfDeath)
                }
            }
            beneficiaryStatus.value = when (saved.isDeath) {
                true -> BenStatus.Death.name
                false -> BenStatus.Alive.name
                null -> null
            }
            dateOfDeath.value    = saved.dateOfDeath
            timeOfDeath.value    = saved.timeOfDeath
            reasonOfDeath.value  = saved.reasonOfDeath
            placeOfDeath.value   = saved.placeOfDeath
            otherPlaceOfDeath.value = saved.otherPlaceOfDeath

            pic.value        = saved.userImage
            dateOfReg.value  = getDateFromLong(saved.regDate)
            firstName.value  = saved.firstName
            lastName.value   = saved.lastName
            agePopup.value   = getDateFromLong(saved.dob)
            gender.value     = gender.getStringFromPosition(saved.genderId)

            maritalStatus.entries = when (saved.gender) {
                MALE -> maritalStatusMale
                FEMALE -> maritalStatusFemale
                else -> maritalStatusMale
            }
            maritalStatus.arrayId = when (saved.gender) {
                MALE -> R.array.nbr_marital_status_male_array
                FEMALE -> R.array.nbr_marital_status_female_array
                else -> R.array.nbr_marital_status_male_array
            }
            maritalStatus.value = saved.genDetails?.maritalStatusId
                ?.let { maritalStatus.getStringFromPosition(it) }

            if (saved.isSpouseAdded || saved.isChildrenAdded || saved.doYouHavechildren) {
                maritalStatus.inputType = TEXT_VIEW
            }

            // Spouse name
            saved.genDetails?.spouseName?.let {
                when (saved.genderId) {
                    1 -> wifeName.value = it
                    2 -> husbandName.value = it
                    3 -> spouseName.value = it
                }
            }
            fatherName.value = saved.fatherName
            motherName.value = saved.motherName
            subCentre.value = saved.locationRecord.block.name

            mobileNoOfRelation.value = mobileNoOfRelation.getStringFromPosition(saved.mobileNoOfRelationId)
            otherMobileNoOfRelation.value = saved.mobileOthers
            contactNumber.value = saved.contactNumber?.toString()

            community.value  = community.getStringFromPosition(saved.communityId)
            religion.value   = religion.getStringFromPosition(saved.religionId)
            otherReligion.value = saved.religionOthers

            economicStatus.value = economicStatus.getStringFromPosition(saved.economicStatusId ?: 0)
            residentialAreaType.value = residentialAreaType.getStringFromPosition(saved.residentialAreaId ?: 0)
            otherResidentialAreaType.value = saved.otherResidentialArea

            if (!saved.occupation.isNullOrEmpty() && saved.occupation != "unknown") occupation.value = saved.occupation

            reproductiveStatus.value = saved.genDetails?.reproductiveStatus?.let {
                normalizeReproductiveStatusForDisplay(it)
            }
        }

        addMaritalStatusIfApplicable(list)

        // Add spouse name field if married
        val maritalIndex = list.indexOf(maritalStatus)
        if (!maritalStatus.value.isNullOrEmpty() && maritalStatus.value == maritalStatus.getStringFromPosition(2) && maritalIndex >= 0) {
            val spouseField = when (gender.value) {
                gender.entries?.get(0) -> wifeName
                gender.entries?.get(1) -> husbandName
                gender.entries?.get(2) -> spouseName
                else -> null
            }
            spouseField?.let {
                list.add(maritalIndex + 1, it)
            }
        }

        // Mobile other
        val mobileIndex = list.indexOf(mobileNoOfRelation)
        if (mobileNoOfRelation.value == mobileNoOfRelation.entries!!.last() && mobileIndex >= 0) {
            list.add(mobileIndex + 1, otherMobileNoOfRelation)
        }

        // Religion other
        val religionIndex = list.indexOf(religion)
        if (religion.value == religion.entries!![7] && religionIndex >= 0) {
            list.add(religionIndex + 1, otherReligion)
        }

        // Residential area other
        val residentialIndex = list.indexOf(residentialAreaType)
        if (residentialAreaType.value == residentialAreaType.entries!!.last() && residentialIndex >= 0) {
            list.add(residentialIndex + 1, otherResidentialAreaType)
        }

        addReproductiveStatusIfApplicable(list)

        setUpPage(list)
    }

    // Keep setFirstPageToRead as alias for backward compat with ViewModel
    suspend fun setFirstPageToRead(ben: BenRegCache?, familyHeadPhoneNo: Long?, villageName: String? = null, villageNames: Array<String>? = null, villageEntityList: List<LocationEntity> = emptyList(), subCentreName: String? = null) =
        setUpPage(ben, familyHeadPhoneNo, villageName, villageNames, villageEntityList, subCentreName)


    private var familyHeadPhoneNo: String? = null
    private var villageEntities: List<LocationEntity> = emptyList()

    fun hasThirdPage(): Boolean {
        return (getAgeFromDob(getLongFromDate(agePopup.value)) >= Konstants.minAgeForGenBen
                && gender.value == gender.entries!![1])
    }

    private fun shouldShowMaritalStatus(): Boolean {
        return agePopup.value?.let { getAgeFromDob(getLongFromDate(it)) >= Konstants.minAgeForGenBen } == true
    }

    private fun shouldRequireMaritalStatus(): Boolean {
        val isFemaleSelected = gender.value == gender.entries?.getOrNull(1)
        val age = agePopup.value?.let { runCatching { getAgeFromDob(getLongFromDate(it)) }.getOrDefault(0) } ?: 0
        return isFemaleSelected && age >= Konstants.minAgeForGenBen
    }

    private fun updateMaritalStatusRequirement() {
        maritalStatus.required = shouldRequireMaritalStatus()
    }

    private fun addMaritalStatusIfApplicable(list: MutableList<FormElement>) {
        if (!shouldShowMaritalStatus()) {
            maritalStatus.value = null
            return
        }
        updateMaritalStatusRequirement()
        if (list.contains(maritalStatus)) return

        val villageIndex = list.indexOf(villageHamlet)
        val insertIndex = if (villageIndex >= 0) villageIndex + 1 else list.size
        list.add(insertIndex, maritalStatus)
    }

    private fun shouldShowReproductiveStatus(): Boolean {
        val selectedGender = gender.value
        val selectedMaritalStatus = maritalStatus.value
        val marriedValue = maritalStatus.entries?.getOrNull(1)
        return selectedGender == gender.entries?.getOrNull(1) &&
            selectedMaritalStatus == marriedValue
    }

    private fun addReproductiveStatusIfApplicable(list: MutableList<FormElement>) {
        if (!shouldShowReproductiveStatus()) {
            reproductiveStatus.value = null
            return
        }
        if (list.contains(reproductiveStatus)) return

        val insertIndex = list.indexOf(maritalStatus)
        list.add(insertIndex + 1, reproductiveStatus)
    }

    private fun normalizeReproductiveStatusForDisplay(savedValue: String): String {
        val yesValue = resources.getStringArray(R.array.nbr_reproductive_status_married_women_array)[0]
        val noValue = resources.getStringArray(R.array.nbr_reproductive_status_married_women_array)[1]
        return when (savedValue.trim().lowercase(Locale.ENGLISH)) {
            "pregnant woman", "women pregnant", "yes" -> yesValue
            "eligible couple", "postnatal mother", "permanently sterilised", "not applicable",
            "women not pregnant", "no" -> noValue
            else -> savedValue
        }
    }


    // ─────────────────────────── VALUE CHANGED ───────────────────────────

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        return when (formId) {

            firstName.id -> {
                validateEmptyOnEditText(firstName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(firstName)
            }

            lastName.id -> {
                validateEmptyOnEditText(lastName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(lastName)
            }

            beneficiaryStatus.id -> {
                val isDeath = beneficiaryStatus.value == BenStatus.Death.name
                if (isDeath) {
                    dateOfDeath.min = getMinDateFromRegistration(dateOfReg.value!!)
                    val showMaternal = shouldShowMaternalDeath(gender.value, agePopup.value)
                    reasonOfDeath.entries = if (showMaternal)
                        resources.getStringArray(R.array.reason_of_death_array_with_maternal)
                    else
                        resources.getStringArray(R.array.reason_of_death_array)
                }
                triggerDependants(
                    source = beneficiaryStatus,
                    passedIndex = if (isDeath) 1 else 0,
                    triggerIndex = 1,
                    target = if (isDeath)
                        listOf(dateOfDeath, timeOfDeath, reasonOfDeath, placeOfDeath)
                    else
                        listOf(dateOfDeath, timeOfDeath, reasonOfDeath, placeOfDeath, otherPlaceOfDeath)
                )
            }

            placeOfDeath.id -> {
                val i = placeOfDeath.entries?.indexOf(placeOfDeath.value).takeIf { it!! >= 0 } ?: return -1
                triggerDependants(source = placeOfDeath, passedIndex = i, triggerIndex = 8, target = otherPlaceOfDeath)
            }

            agePopup.id -> {
                val age = try { getAgeFromDob(getLongFromDate(agePopup.value)) } catch (_: Exception) { 0 }

                // Hide marital status + dependants for age < 15 (PRD: Not Applicable for age 0-14)
                val maritalFields = listOf(
                    maritalStatus, husbandName, wifeName, spouseName,
                    ageAtMarriage, dateOfMarriage, reproductiveStatus
                )
                if (age < Konstants.minAgeForGenBen) {
                    triggerDependants(
                        source = villageHamlet,
                        removeItems = maritalFields,
                        addItems = emptyList()
                    )
                } else {
                    triggerDependants(
                        source = villageHamlet,
                        removeItems = maritalFields,
                        addItems = listOf(maritalStatus)
                    )
                    maritalStatus.entries = when (index) {
                        1 -> maritalStatusFemale
                        else -> maritalStatusMale
                    }
                    maritalStatus.arrayId = when (index) {
                        1 -> R.array.nbr_marital_status_female_array
                        else -> R.array.nbr_marital_status_male_array
                    }
                }
                updateMaritalStatusRequirement()

                try { return updateReproductiveOptionsBasedOnAgeGender(formId = agePopup.id) }
                catch (e: Exception) { e.printStackTrace(); return 1 }
            }

            ageAtMarriage.id -> {
                val dobMillis = getLongFromDate(agePopup.value)
                val currentAge = getAgeFromDob(dobMillis)
                currentAge.takeIf { it > 0 && !ageAtMarriage.value.isNullOrEmpty() }?.let {
                    validateEmptyOnEditText(ageAtMarriage)
                    ageAtMarriage.max = currentAge.toLong()
                    validateIntMinMax(ageAtMarriage)
                    val entered = ageAtMarriage.value!!.toIntOrNull() ?: return@let
                    val dobCal = Calendar.getInstance().apply { timeInMillis = dobMillis }
                    val marriageCal = Calendar.getInstance().apply {
                        timeInMillis = dobMillis
                        set(Calendar.YEAR, dobCal.get(Calendar.YEAR) + entered)
                    }
                    dateOfMarriage.value = getDateFromLong(marriageCal.timeInMillis)
                    dateOfMarriage.max   = Calendar.getInstance().timeInMillis
                    dateOfMarriage.min   = marriageCal.timeInMillis
                    triggerDependants(source = ageAtMarriage, passedIndex = it, triggerIndex = it, target = dateOfMarriage)
                } ?: -1
                return 0
            }

            gender.id -> {
                maritalStatus.value = null
                reproductiveStatus.value = null
                husbandName.value = null
                wifeName.value = null
                spouseName.value = null
                ageAtMarriage.value = null
                dateOfMarriage.value = null
                maritalStatus.inputType = DROPDOWN
                reproductiveStatus.inputType = RADIO

                maritalStatus.entries = when (index) {
                    1 -> maritalStatusFemale
                    else -> maritalStatusMale
                }
                maritalStatus.arrayId = when (index) {
                    1 -> R.array.nbr_marital_status_female_array
                    else -> R.array.nbr_marital_status_male_array
                }
                updateMaritalStatusRequirement()

                triggerDependants(
                    source = maritalStatus,
                    removeItems = listOf(
                        wifeName,
                        husbandName,
                        spouseName,
                        ageAtMarriage,
                        dateOfMarriage,
                        reproductiveStatus
                    ),
                    addItems = emptyList()
                )

                val listChanged = if (hasThirdPage()) {
                    updateReproductiveOptionsBasedOnAgeGender(formId = gender.id)
                    1
                } else {
                    1
                } != -1

                if (listChanged) 1 else -1
            }

            maritalStatus.id -> {
                when (maritalStatus.value) {
                    maritalStatus.entries!![0] -> {
                        fatherName.required = false
                        motherName.required = false
                        updateReproductiveOptionsBasedOnAgeGender(formId = maritalStatus.id)
                        triggerDependants(
                            source = maritalStatus, addItems = emptyList(),
                            removeItems = listOf(spouseName, husbandName, wifeName, ageAtMarriage, dateOfMarriage, reproductiveStatus)
                        )
                    }
                    else -> {
                        fatherName.required = false
                        motherName.required = false
                        husbandName.required = false
                        wifeName.required = false
                        updateReproductiveOptionsBasedOnAgeGender(formId = maritalStatus.id)
                        if (gender.value == gender.entries!![1]) {
                            triggerDependants(
                                source = maritalStatus,
                                addItems = listOf(reproductiveStatus, husbandName),
                                removeItems = listOf(wifeName, husbandName, spouseName, ageAtMarriage, dateOfMarriage, reproductiveStatus)
                            )
                        } else {
                            triggerDependants(
                                source = motherName,
                                addItems = when (gender.value) {
                                    gender.entries!![0] -> listOf(wifeName)
                                    else -> listOf(spouseName)
                                },
                                removeItems = listOf(wifeName, husbandName, spouseName, ageAtMarriage, dateOfMarriage, reproductiveStatus)
                            )
                        }
                    }
                }
            }

            mobileNoOfRelation.id -> {
                when (index) {
                    4 -> {
                        contactNumber.value = familyHeadPhoneNo
                        triggerDependants(source = mobileNoOfRelation, removeItems = listOf(otherMobileNoOfRelation), addItems = emptyList())
                    }
                    in 0..3 -> triggerDependants(source = mobileNoOfRelation, removeItems = listOf(otherMobileNoOfRelation), addItems = emptyList())
                    else -> triggerDependants(source = mobileNoOfRelation, removeItems = emptyList(), addItems = listOf(otherMobileNoOfRelation))
                }
            }

            religion.id -> {
                triggerDependants(source = religion, passedIndex = index, triggerIndex = 7, target = otherReligion)
            }

            residentialAreaType.id -> {
                triggerDependants(
                    source = residentialAreaType,
                    passedIndex = index,
                    triggerIndex = residentialAreaType.entries!!.lastIndex,
                    target = otherResidentialAreaType
                )
            }

            contactNumber.id -> {
                validateEmptyOnEditText(contactNumber)
                validateMobileNumberOnEditText(contactNumber)
            }

            fatherName.id -> {
                validateEmptyOnEditText(fatherName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(fatherName)
            }

            motherName.id -> {
                validateEmptyOnEditText(motherName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(motherName)
            }

            husbandName.id -> {
                validateEmptyOnEditText(husbandName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(husbandName)
            }

            wifeName.id -> {
                validateEmptyOnEditText(wifeName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(wifeName)
            }

            spouseName.id -> {
                validateEmptyOnEditText(spouseName)
                validateAllCapsOrSpaceOnEditTextWithHindiEnabled(spouseName)
            }

            otherMobileNoOfRelation.id -> validateEmptyOnEditText(otherMobileNoOfRelation)
            otherReligion.id -> validateEmptyOnEditText(otherReligion)
            otherResidentialAreaType.id -> validateEmptyOnEditText(otherResidentialAreaType)
            else -> -1
        }
    }

    // ─────────────────────────── MAP VALUES ───────────────────────────

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as BenRegCache).let { ben ->

            // Death fields
            ben.isDeathValue = beneficiaryStatus.value
            ben.isDeath = beneficiaryStatus.entries?.indexOf(beneficiaryStatus.value ?: "") == 1
            ben.dateOfDeath   = dateOfDeath.value
            ben.timeOfDeath   = timeOfDeath.value
            ben.reasonOfDeath = reasonOfDeath.value
            ben.reasonOfDeathId = reasonOfDeath.entries?.indexOf(reasonOfDeath.value ?: "")?.takeIf { it != -1 } ?: -1
            ben.placeOfDeath  = placeOfDeath.value
            ben.placeOfDeathId = placeOfDeath.entries?.indexOf(placeOfDeath.value ?: "")?.takeIf { it != -1 } ?: -1
            ben.otherPlaceOfDeath = otherPlaceOfDeath.value

            // Basic info
            ben.userImage  = pic.value
            ben.regDate    = getLongFromDate(dateOfReg.value!!)
            ben.firstName  = firstName.value
            ben.lastName   = lastName.value
            ben.dob        = getLongFromDate(agePopup.value!!)
            ben.age        = getAgeFromDob(getLongFromDate(agePopup.value))
            ben.ageUnitId  = 3
            ben.ageUnit    = AgeUnit.YEARS
            ben.isAdult    = ben.age >= 15
            ben.isKid      = false // StopTB has no kid concept

            ben.genderId = when (gender.value) {
                gender.entries!![0] -> 1
                gender.entries!![1] -> 2
                gender.entries!![2] -> 3
                gender.entries!![3] -> 4
                else -> 0
            }
            ben.gender = when (ben.genderId) {
                1 -> MALE; 2 -> FEMALE; 3 -> TRANSGENDER; 4 -> PREFER_NOT_TO_SAY; else -> null
            }

            ben.fatherName = fatherName.value
            ben.motherName = motherName.value

            // No relation to head in StopTB — set to default
            ben.familyHeadRelationPosition = 19
            ben.familyHeadRelation = null
            ben.familyHeadRelationOther = null

            // Mobile
            ben.mobileNoOfRelationId = mobileNoOfRelation.getPosition()
            ben.mobileNoOfRelation   = mobileNoOfRelation.getEnglishStringFromPosition(ben.mobileNoOfRelationId)
            ben.mobileOthers         = otherMobileNoOfRelation.value
            ben.contactNumber        = when {
                ben.mobileNoOfRelationId == 5 -> familyHeadPhoneNo?.toLongOrNull()
                contactNumber.value.isNullOrEmpty() -> null
                else -> contactNumber.value!!.toLong()
            }
            ben.tempMobileNoOfRelationId = 0

            // Community & Religion
            ben.communityId    = community.getPosition()
            ben.community      = community.getEnglishStringFromPosition(ben.communityId)
            ben.religionId     = religion.getPosition()
            ben.religion       = religion.getEnglishStringFromPosition(ben.religionId)
            ben.religionOthers = otherReligion.value

            // Economic Status & Residential Area
            ben.economicStatusId   = economicStatus.getPosition()
            ben.economicStatus     = economicStatus.getEnglishStringFromPosition(ben.economicStatusId ?: 0)
            ben.residentialAreaId  = residentialAreaType.getPosition()
            ben.residentialArea    = residentialAreaType.getEnglishStringFromPosition(ben.residentialAreaId ?: 0)
            ben.otherResidentialArea = otherResidentialAreaType.value

            // Village — update locationRecord if user changed village
            villageHamlet.value?.let { selectedName ->
                villageEntities.find { it.name == selectedName }?.let { selectedVillage ->
                    ben.locationRecord = ben.locationRecord.copy(village = selectedVillage)
                }
            }
            subCentre.value?.takeIf { it.isNotBlank() }?.let { selectedSubCentre ->
                ben.locationRecord = ben.locationRecord.copy(
                    block = LocationEntity(
                        id = ben.locationRecord.block.id,
                        name = selectedSubCentre
                    )
                )
            }

            // Occupation
            val defaultOccupation = resources.getString(R.string.nbr_occupation_default)
            ben.occupation = occupation.value?.ifEmpty { defaultOccupation } ?: defaultOccupation

            // Marital Status
            ben.genDetails?.maritalStatusId = maritalStatus.getPosition()
            ben.genDetails?.maritalStatus   = maritalStatus.getEnglishStringFromPosition(ben.genDetails?.maritalStatusId ?: 0)
            ben.genDetails?.spouseName      = husbandName.value.takeIf { !it.isNullOrEmpty() }
                ?: wifeName.value.takeIf { !it.isNullOrEmpty() }
                        ?: spouseName.value.takeIf { !it.isNullOrEmpty() }
            ben.genDetails?.marriageDate = 0
            ben.genDetails?.ageAtMarriage = 0

            // Reproductive Status
            ben.genDetails?.let { gen ->
                val selectedValue = reproductiveStatus.value?.trim() ?: ""
                val reproductiveMap = mapOf(
                    "Yes" to 1,
                    "No" to 2,
                    "Women Pregnant" to 1,
                    "Women Not Pregnant" to 2,
                    "Pregnant Woman" to 1,
                    "Eligible Couple" to 2,
                    "Postnatal Mother" to 2,
                    "Permanently Sterilised" to 2,
                    "Not Applicable" to 2
                )
                gen.reproductiveStatusId = reproductiveMap[selectedValue] ?: 0
                gen.reproductiveStatus   = selectedValue
            }

            // No kid details in StopTB
            ben.kidDetails = null

            ben.isDraft        = false
            ben.isConsent      = isOtpVerified
            ben.isSpouseAdded  = false
            ben.isChildrenAdded = false
            ben.isMarried      = (maritalStatus.getPosition() == 2)
            ben.doYouHavechildren = false
        }
    }

    // ─────────────────────────── HELPERS ───────────────────────────

    fun getIndexOfAgeAtMarriage()  = -1
    fun getIndexOfContactNumber()  = getIndexOfElement(contactNumber)
    fun getIndexOfMaritalStatus()  = getIndexOfElement(maritalStatus)

    fun enableEditMode() {
        if (fatherName.inputType == TEXT_VIEW) fatherName.inputType = EDIT_TEXT
        if (motherName.inputType == TEXT_VIEW) motherName.inputType = EDIT_TEXT
    }
    fun getTempMobileNoStatus()    = getIndexOfElement(contactNumber) // no temp contact in StopTB
    fun getIndexOfBirthCertificateFrontPath() = -1 // not used in StopTB
    fun getIndexOfBirthCertificateBackPath()  = -1 // not used in StopTB

    fun setImageUriToFormElement(lastImageFormId: Int, dpUri: Uri) {
        pic.value = dpUri.toString()
        pic.errorText = null
    }

    fun hasBeneficiaryPhoto(): Boolean = !pic.value.isNullOrBlank()

    private fun calculateMarriageDate(marriageAge: Int, dob: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dob
        calendar.add(Calendar.YEAR, marriageAge)
        return calendar.timeInMillis
    }

    private fun calculateAgeAtMarriage(dob: Long, marriageDate: Long?): Int? {
        if (marriageDate == null || marriageDate <= 0L) return null
        val dobCal = Calendar.getInstance().apply { timeInMillis = dob }
        val marCal = Calendar.getInstance().apply { timeInMillis = marriageDate }
        var age = marCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
        if (marCal.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) age--
        return age.takeIf { it >= Konstants.minAgeForMarriage }
    }

    private fun updateReproductiveOptionsBasedOnAgeGender(formId: Int): Int {
        reproductiveStatus.arrayId = R.array.nbr_reproductive_status_married_women_array
        reproductiveStatus.entries =
            resources.getStringArray(R.array.nbr_reproductive_status_married_women_array)
        if (!shouldShowReproductiveStatus()) {
            reproductiveStatus.value = null
        } else if (reproductiveStatus.value.isNullOrBlank()) {
            reproductiveStatus.value = reproductiveStatus.entries?.getOrNull(1)
        }
        return 1
    }

    private fun shouldShowMaternalDeath(gender: String?, dob: String?): Boolean {
        if (gender != "Female") return false
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            val dobMillis = sdf.parse(dob ?: return false)?.time ?: return false
            getAgeFromDob(dobMillis) in 15..49
        } catch (e: Exception) { false }
    }

    private fun getMinDateFromRegistration(registrationDate: String): Long {
        return try {
            SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(registrationDate)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}
