package org.piramalswasthya.stoptb.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemBenBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.Gender
import timber.log.Timber
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache

data class ButtonConfig(
    val text: String,
    val colorRes: Int,
    val action: String,
    val type: String
)

class BenListAdapter(
    private val clickListener: BenClickListener? = null,
    private val showBeneficiaries: Boolean = false,
    private val showRegistrationDate: Boolean = false,
    private val showSyncIcon: Boolean = false,
    private val showAbha: Boolean = false,
    private val showCall: Boolean = false,
    private val isSoftDeleteEnabled: Boolean = false,
    private val pref: PreferenceDao? = null,
    private val context: FragmentActivity,
    private val role: Int? = null,
    private val showActionButtons: Boolean = true,
    private val showResultButton: Boolean = false,
    private val showAnthropometryButton: Boolean = false,
    private val showExamineButton: Boolean = true
) : ListAdapter<BenBasicDomain, BenListAdapter.BenViewHolder>(BenDiffUtilCallBack) {

    object BenDiffUtilCallBack : DiffUtil.ItemCallback<BenBasicDomain>() {
        override fun areItemsTheSame(
            oldItem: BenBasicDomain, newItem: BenBasicDomain
        ) = oldItem.benId == newItem.benId

        override fun areContentsTheSame(
            oldItem: BenBasicDomain, newItem: BenBasicDomain
        ) = oldItem == newItem
    }

    class BenViewHolder private constructor(private val binding: RvItemBenBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): BenViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemBenBinding.inflate(layoutInflater, parent, false)
                return BenViewHolder(binding)
            }
        }

        fun bind(
            item: BenBasicDomain,
            clickListener: BenClickListener?,
            showAbha: Boolean,
            showSyncIcon: Boolean,
            showRegistrationDate: Boolean,
            showBeneficiaries: Boolean, role: Int?,
            showCall: Boolean,
            isSoftDeleteEnabled: Boolean,
            pref: PreferenceDao?,
            context: FragmentActivity,
            benIdList: List<Long>,
            tbScreeningBenIds: List<Long> = emptyList(),
            generalOpdBenIds: List<Long> = emptyList(),
            anthropometryBenIds: List<Long> = emptyList(),
            tbSuspectedBenIds: List<Long> = emptyList(),
            childCountMap: Map<Long, Int> = emptyMap(),
            showActionButtons: Boolean = true,
            showResultButton: Boolean = false,
            showAnthropometryButton: Boolean = false,
            showExamineButton: Boolean = true,
            tbDiagnosticsList: List<TBDiagnosticsCache> = emptyList(),
            source: Int = 0
        ) {

            binding.btnAbha.visibility = View.VISIBLE
            if (!showSyncIcon) item.syncState = null
            binding.ben = item
            binding.clickListener = clickListener
            binding.showAbha = showAbha
            binding.showActionButtons = showActionButtons
            binding.showRegistrationDate = showRegistrationDate
            binding.registrationDate.visibility =
                if (showRegistrationDate) View.VISIBLE else View.INVISIBLE
            binding.hasAbha = !item.abhaId.isNullOrEmpty()
            binding.role = role

            binding.ivCall.visibility = if (showCall && item.hasCallableMobileNo) {
                View.VISIBLE
            } else {
                View.GONE
            }

            val isMatched = benIdList.contains(item.benId)
            binding.isMatched = isMatched
            val hasTbScreening = tbScreeningBenIds.contains(item.benId)
            val hasGeneralOpd = generalOpdBenIds.contains(item.benId)
            val hasAnthropometry = anthropometryBenIds.contains(item.benId)
            val hasDiagnosis = tbSuspectedBenIds.contains(item.benId)
            binding.isGeneralOpdDone = hasGeneralOpd
            binding.isAnthropometryDone = hasAnthropometry

            binding.btnAbove30.text = if (isMatched) {
                binding.root.context.getString(R.string.view_edit_eye_surgery)
            } else {
                binding.root.context.getString(R.string.add_eye_surgery)
            }
            val isNonHH = item.isNonHH
            val isHeadOfFamily = if (isNonHH) false else item.relToHeadId == 19
            val hasFamilyHeadName = !isNonHH && item.familyHeadName.isNotBlank() && item.familyHeadName != "Not Available"
            binding.HOF.visibility = View.GONE
            if (isNonHH) {
                binding.ivIsHead.visibility = View.VISIBLE
                binding.ivIsHead.setImageResource(R.drawable.ic_no_hh)
                binding.ivIsHead.imageTintList = null
            } else {
                binding.ivIsHead.setImageResource(R.drawable.ic__hh)
                binding.ivIsHead.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_primary)
                )
                binding.ivIsHead.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
            }
            binding.head.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
            binding.ncdHofName.visibility = if (!isHeadOfFamily && hasFamilyHeadName) View.VISIBLE else View.GONE
            binding.btnAbove30.visibility = View.GONE
            binding.btnVitalScreen.visibility = when {
                showResultButton && !item.isDeath && !item.isDeactivate -> View.VISIBLE
                else -> View.GONE
            }

            binding.btnGeneralOpd.visibility = View.GONE
            binding.llGeneralOpdRow.visibility = View.GONE
            binding.llGeneralOpdAction.visibility = View.GONE

            binding.btnAnthropometry.visibility = View.GONE
            binding.llAnthropometryAction.visibility = View.GONE

            if (binding.btnVitalScreen.visibility == View.VISIBLE) {
                if (showResultButton) {
                    val tbDiag = tbDiagnosticsList.find { it.benId == item.benId }
                    val config = when (source) {
                        6 -> {
                            val status = tbDiag?.xrayOrderStatus
                            val referred = tbDiag?.isReferredForDigitalChestXray
                            when {
                                status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PROCESSING", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                    ButtonConfig("FETCHING RESULT...", android.R.color.darker_gray, "POLL", "XRAY_CHEST")
                                }
                                status.equals("COMPLETED", ignoreCase = true) -> {
                                    ButtonConfig("VIEW RESULT", android.R.color.holo_green_dark, "VIEW", "XRAY_CHEST")
                                }
                                status.equals("FAILED", ignoreCase = true) -> {
                                    if (tbDiag?.xrayOrderId.isNullOrBlank()) {
                                        ButtonConfig("RETRY REFERRAL", android.R.color.holo_red_dark, "REFER", "XRAY_CHEST")
                                    } else {
                                        ButtonConfig("RESULT UNAVAILABLE", android.R.color.darker_gray, "NO_RESULT", "XRAY_CHEST")
                                    }
                                }
                                referred == false -> {
                                    ButtonConfig("RETRY REFERRAL", android.R.color.holo_red_dark, "REFER", "XRAY_CHEST")
                                }
                                (referred == true && !status.equals("FAILED", ignoreCase = true)) || status.equals("PENDING", ignoreCase = true) || status.equals("CREATED", ignoreCase = true) || status.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) -> {
                                    ButtonConfig("MARK X-RAY AS COMPLETED", android.R.color.holo_orange_dark, "COMPLETE", "XRAY_CHEST")
                                }
                                else -> {
                                    ButtonConfig("REFER FOR X-RAY", android.R.color.holo_blue_dark, "REFER", "XRAY_CHEST")
                                }
                            }
                        }
                        7 -> {
                            val status = tbDiag?.trueNatOrderStatus
                            val sputumCollected = tbDiag?.isSputumCollected
                            val naatRes = tbDiag?.naatResult
                            val rifRes = tbDiag?.trueNatRifResult
                            when {
                                status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PROCESSING", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                    ButtonConfig("FETCHING RESULT...", android.R.color.darker_gray, "POLL", "SPUTUM_TRUENAT")
                                }
                                status.equals("COMPLETED", ignoreCase = true) -> {
                                    ButtonConfig("VIEW RESULT", android.R.color.holo_green_dark, "VIEW", "SPUTUM_TRUENAT")
                                }
                                status.equals("FAILED", ignoreCase = true) -> {
                                    if (tbDiag?.trueNatOrderId.isNullOrBlank()) {
                                        ButtonConfig("RETRY REFERRAL", android.R.color.holo_red_dark, "REFER", "SPUTUM_TRUENAT")
                                    } else {
                                        ButtonConfig("RESULT UNAVAILABLE", android.R.color.darker_gray, "NO_RESULT", "SPUTUM_TRUENAT")
                                    }
                                }
                                sputumCollected == false -> {
                                    ButtonConfig("RETRY REFERRAL", android.R.color.holo_red_dark, "REFER", "SPUTUM_TRUENAT")
                                }
                                (sputumCollected == true && !status.equals("FAILED", ignoreCase = true)) || status.equals("PENDING", ignoreCase = true) || status.equals("CREATED", ignoreCase = true) || status.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) -> {
                                    ButtonConfig("MARK TRUENAT TEST AS COMPLETED", android.R.color.holo_orange_dark, "COMPLETE", "SPUTUM_TRUENAT")
                                }
                                else -> {
                                    ButtonConfig("REFER FOR SPUTUM", android.R.color.holo_blue_dark, "REFER", "SPUTUM_TRUENAT")
                                }
                            }
                        }
                        8 -> {
                            val hasLc = !tbDiag?.liquidCultureResult.isNullOrBlank()
                            if (hasLc) {
                                ButtonConfig("VIEW/EDIT RESULT", android.R.color.holo_green_dark, "VIEW_LC", "LIQUID_CULTURE")
                            } else {
                                ButtonConfig("ENTER RESULT", android.R.color.holo_red_dark, "ENTER_LC", "LIQUID_CULTURE")
                            }
                        }
                        else -> ButtonConfig("RESULT", android.R.color.holo_green_dark, "VIEW", "")
                    }

                    binding.btnVitalScreen.text = config.text
                    binding.btnVitalScreen.setBackgroundTintList(
                        ContextCompat.getColorStateList(binding.root.context, config.colorRes)
                    )
                    binding.btnVitalScreen.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )

                    val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
                    val isViewAction = config.action == "VIEW" || config.action == "VIEW_LC" || config.action == "ENTER_LC"
                    if (config.action == "POLL" || config.action == "NO_RESULT") {
                        binding.btnVitalScreen.isEnabled = false
                        binding.btnVitalScreen.alpha = 0.5f
                    } else if (!isNurse && !isViewAction) {
                        binding.btnVitalScreen.isEnabled = false
                        binding.btnVitalScreen.alpha = 0.5f
                    } else {
                        binding.btnVitalScreen.isEnabled = true
                        binding.btnVitalScreen.alpha = 1.0f
                    }

                    fun formatDenialReason(reason: String?, other: String?): String {
                        if (reason.isNullOrBlank()) return ""
                        return reason.split("|").joinToString("\n") { r ->
                            if (r.equals("Other", ignoreCase = true) && !other.isNullOrBlank()) {
                                "Other: $other"
                            } else {
                                r
                            }
                        }
                    }

                    val statusText = when (source) {
                        6 -> {
                            val status = tbDiag?.xrayOrderStatus
                            val referred = tbDiag?.isReferredForDigitalChestXray
                            when {
                                referred == false -> {
                                    val reasonStr = formatDenialReason(tbDiag.reasonForDenialChestXray, tbDiag.reasonForDenialChestXrayOther)
                                    "Referral Status: Declined\nReason:\n$reasonStr"
                                }
                                 referred == true -> {
                                    when {
                                        status.equals("PENDING", ignoreCase = true) || status.equals("CREATED", ignoreCase = true) || status.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) -> {
                                            "Referral Status: Referred\nOrder Status: Awaiting Test Completion"
                                        }
                                        status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PROCESSING", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                            "Referral Status: Referred\nOrder Status: Awaiting Provider Result\nFetching Digital Chest X-ray Result..."
                                        }
                                        status.equals("COMPLETED", ignoreCase = true) -> {
                                            "Referral Status: Completed\nResult Status: Available"
                                        }
                                        status.equals("FAILED", ignoreCase = true) -> {
                                            if (tbDiag?.xrayOrderId.isNullOrBlank()) {
                                                "Referral Status: Order Push Failed (Retry Required)"
                                            } else {
                                                "Referral Status: Referred\nResult Status: Result Unavailable"
                                            }
                                        }
                                        else -> "Referral Status: Referred"
                                    }
                                }
                                else -> {
                                    "Referral Status: Pending"
                                }
                            }
                        }
                        7 -> {
                            val status = tbDiag?.trueNatOrderStatus
                            val sputumCollected = tbDiag?.isSputumCollected
                            when {
                                sputumCollected == false -> {
                                    val reasonStr = formatDenialReason(tbDiag.reasonForDenialSputum, tbDiag.reasonForDenialSputumOther)
                                    "Referral Status: Declined\nReason:\n$reasonStr"
                                }
                                sputumCollected == true -> {
                                    when {
                                        status.equals("PENDING", ignoreCase = true) || status.equals("CREATED", ignoreCase = true) || status.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) -> {
                                            "Referral Status: Referred\nOrder Status: Awaiting Test Completion"
                                        }
                                        status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PROCESSING", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                            "Referral Status: Referred\nOrder Status: Awaiting Provider Result\nFetching TrueNat Result..."
                                        }
                                        status.equals("COMPLETED", ignoreCase = true) -> {
                                            "Referral Status: Completed\nResult Status: Available"
                                        }
                                        status.equals("FAILED", ignoreCase = true) -> {
                                            if (tbDiag?.trueNatOrderId.isNullOrBlank()) {
                                                "Referral Status: Order Push Failed (Retry Required)"
                                            } else {
                                                "Referral Status: Referred\nResult Status: Result Unavailable"
                                            }
                                        }
                                        else -> "Referral Status: Referred"
                                    }
                                }
                                else -> {
                                    "Referral Status: Pending"
                                }
                            }
                        }
                        else -> null
                    }

                    if (statusText != null) {
                        binding.tvOrderStatus.text = statusText
                        binding.tvOrderStatus.visibility = View.VISIBLE
                    } else {
                        binding.tvOrderStatus.visibility = View.GONE
                    }

                    binding.btnVitalScreen.setOnClickListener {
                        clickListener?.onClickOrderAction(item, config.action, config.type)
                    }
                } else {
                    binding.tvOrderStatus.visibility = View.GONE
                    binding.btnVitalScreen.text = binding.root.context.getString(R.string.vital_screen)
                    val hasVitals = benIdList.contains(item.benId)
                    binding.btnVitalScreen.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                            binding.root.context,
                            if (hasVitals) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                        )
                    )
                    binding.btnVitalScreen.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )
                    binding.btnVitalScreen.setOnClickListener {
                        clickListener?.onClickVitalScreen(item)
                    }
                }
            }
            if (binding.btnGeneralOpd.visibility == View.VISIBLE) {
                binding.btnGeneralOpd.text = binding.root.context.getString(R.string.general_opd)
                binding.btnGeneralOpd.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        binding.root.context,
                        if (hasGeneralOpd) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                    )
                )
                binding.btnGeneralOpd.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            }
            if (binding.btnAnthropometry.visibility == View.VISIBLE) {
                binding.btnAnthropometry.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        binding.root.context,
                        if (hasAnthropometry) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                    )
                )
                binding.btnAnthropometry.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            }
            // Examine button — show filled count X/total
            // Registrar: Anthropometry + TB Screening
            // Nurse: Diagnosis hidden, so total stays 4
            // Others: all 5 forms
            val currentRole = pref?.getLoggedInUser()?.role
            val isCounsellingOfficer = currentRole.isCounsellingOfficerRole()
            val isRegistrar = pref?.getLoggedInUser()?.role.isRegistrationOfficerRole()
            val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
            val (examineFilledCount, examineTotal) = if (isRegistrar) {
                val filled = listOf(
                    hasAnthropometry,
                    hasTbScreening
                ).count { it }
                Pair(filled, 2)
            } else if (isNurse || isCounsellingOfficer) {
                val filled = listOf(
                    hasAnthropometry,
                    isMatched,
                    hasTbScreening,
                    hasGeneralOpd
                ).count { it }
                Pair(filled, 4)
            } else {
                val filled = listOf(
                    hasAnthropometry,
                    isMatched,
                    hasTbScreening,
                    hasGeneralOpd
                ).count { it }
                Pair(filled, 4)
            }

            binding.btnExamine.text = binding.root.context.getString(
                R.string.btn_examine_count_of, examineFilledCount, examineTotal
            )
            binding.btnExamine.backgroundTintList = ContextCompat.getColorStateList(
                binding.root.context,
                if (examineFilledCount > 0) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            binding.btnExamine.text = "Examine ($examineFilledCount/$examineTotal)"
            val isExamineFilled = examineFilledCount > 0
            binding.btnExamine.setBackgroundTintList(
                ContextCompat.getColorStateList(
                    binding.root.context,
                    if (isExamineFilled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
            )
            binding.btnExamine.setTextColor(
                ContextCompat.getColor(binding.root.context, android.R.color.white)
            )
            binding.btnExamine.visibility = if (showExamineButton) View.VISIBLE else View.GONE

            binding.llBenDetails4.visibility = View.GONE
            binding.btnAddChildren.visibility = View.GONE

            // Register Wife / Register Husband — Registrar only (hidden for Nurse & Counselling officer)
            val isNurseRole = currentRole.isNurseRole()
            when {
                !isNurseRole && !isCounsellingOfficer && !item.isNonHH && item.gender == "MALE" && item.isMarried && !item.isSpouseAdded
                        && !item.isDeath && !item.isDeactivate -> {
                    binding.llAddSpouseBtn.visibility = View.VISIBLE
                    binding.btnAddSpouse.visibility = View.VISIBLE
                    binding.btnAddSpouse.text = context.getString(R.string.add_wife)
                    binding.btnAddSpouse.setOnClickListener {
                        clickListener?.onClickedWifeBen(item)
                    }
                }
                (!isNurseRole && !isCounsellingOfficer) && !item.isNonHH && item.gender == "FEMALE" && item.isMarried && !item.isSpouseAdded
                        && !item.isDeath && !item.isDeactivate -> {
                    binding.llAddSpouseBtn.visibility = View.VISIBLE
                    binding.btnAddSpouse.visibility = View.VISIBLE
                    binding.btnAddSpouse.text = context.getString(R.string.add_husband)
                    binding.btnAddSpouse.setOnClickListener {
                        clickListener?.onClickedHusbandBen(item)
                    }
                }
                else -> {
                    binding.llAddSpouseBtn.visibility = View.GONE
                    binding.btnAddSpouse.visibility = View.GONE
                }
            }
            binding.ivSoftDelete.visibility = View.GONE
            binding.tvTitleDuplicaterecord.visibility = View.GONE

            // Set gender-based avatar icon
            if (item.dob != null) {
                val type = getPatientTypeByAge(getDateFromLong(item.dob))
                val gender = item.gender.toString()
                val iconRes = when (type) {
                    "new_born_baby" -> R.drawable.ic_icon_baby
                    "infant" -> R.drawable.ic_infant
                    "child", "adolescence" -> when (gender) {
                        Gender.MALE.name -> R.drawable.ic_icon_boy_ben
                        Gender.FEMALE.name -> R.drawable.ic_girl
                        else -> null
                    }
                    "adult" -> when (gender) {
                        Gender.MALE.name -> R.drawable.ic_males
                        Gender.FEMALE.name -> R.drawable.ic_icon_female_2
                        else -> R.drawable.ic_unisex
                    }
                    else -> null
                }
                iconRes?.let { binding.ivHhLogo.setImageResource(it) }
            }

            // Father/Husband/Spouse name display
            if (showBeneficiaries) {
                when {
                    item.spouseName == "Not Available" && item.fatherName == "Not Available" -> {
                        binding.father = true; binding.husband = false; binding.spouse = false
                    }
                    item.gender == "MALE" -> {
                        binding.father = true; binding.husband = false; binding.spouse = false
                    }
                    item.gender == "FEMALE" && item.ageInt > 15 -> {
                        binding.father = item.fatherName != "Not Available" && item.spouseName == "Not Available"
                        binding.husband = item.spouseName != "Not Available"
                        binding.spouse = false
                    }
                    item.gender == "FEMALE" -> {
                        binding.father = true; binding.husband = false; binding.spouse = false
                    }
                    else -> {
                        binding.father = item.fatherName != "Not Available" && item.spouseName == "Not Available"
                        binding.spouse = item.spouseName != "Not Available"
                        binding.husband = false
                    }
                }
            } else {
                binding.father = false; binding.husband = false; binding.spouse = false
            }

            // Death/Deactivate background
            when {
                item.isDeath -> {
                    binding.contentLayout.setBackgroundColor(ContextCompat.getColor(binding.contentLayout.context, R.color.md_theme_dark_outline))
                    binding.ivCall.visibility = View.GONE
                    binding.ivSyncState.visibility = View.GONE
                    binding.btnAbha.visibility = View.GONE
                }
                item.isDeactivate -> {
                    binding.contentLayout.setBackgroundColor(ContextCompat.getColor(binding.contentLayout.context, R.color.Quartenary))
                    binding.btnAbha.visibility = View.INVISIBLE
                    binding.tvTitleDuplicaterecord.visibility = View.VISIBLE
                    binding.ivCall.visibility = View.INVISIBLE
                    binding.ivSyncState.visibility = View.INVISIBLE
                }
                else -> {
                    binding.contentLayout.setBackgroundColor(ContextCompat.getColor(binding.contentLayout.context, R.color.md_theme_light_primary))
                }
            }

            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = BenViewHolder.from(parent)

    private val benIds            = mutableListOf<Long>()
    private val tbScreeningIds    = mutableListOf<Long>()
    private val generalOpdIds     = mutableListOf<Long>()
    private val anthropometryIds  = mutableListOf<Long>()
    private val diagnosisIds      = mutableListOf<Long>()
    private val tbDiagnosticsList = mutableListOf<TBDiagnosticsCache>()
    var source: Int = 0

    override fun onBindViewHolder(holder: BenViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            clickListener,
            showAbha,
            showSyncIcon,
            showRegistrationDate,
            showBeneficiaries,
            role,
            showCall,
            isSoftDeleteEnabled,
            pref,
            context,
            benIds,
            tbScreeningIds,
            generalOpdIds,
            anthropometryIds,
            diagnosisIds,
            showActionButtons = showActionButtons,
            showResultButton = showResultButton,
            showAnthropometryButton = showAnthropometryButton,
            tbDiagnosticsList = tbDiagnosticsList,
            source = source
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun applyIdList(target: MutableList<Long>, source: List<Long>) {
        val oldIds = target.toSet()
        target.clear()
        target.addAll(source)
        val newIds = target.toSet()
        val changed = (oldIds - newIds) + (newIds - oldIds)
        if (changed.isNotEmpty()) {
            currentList.forEachIndexed { index, item ->
                if (item.benId in changed) notifyItemChanged(index)
            }
        }
    }

    fun submitTBDiagnostics(list: List<TBDiagnosticsCache>) {
        tbDiagnosticsList.clear()
        tbDiagnosticsList.addAll(list)
        notifyDataSetChanged()
    }

    fun submitBenIds(list: List<Long>)           = applyIdList(benIds, list)
    fun submitTbScreeningBenIds(list: List<Long>) = applyIdList(tbScreeningIds, list)
    fun submitGeneralOpdBenIds(list: List<Long>)  = applyIdList(generalOpdIds, list)
    fun submitAnthropometryBenIds(list: List<Long>) = applyIdList(anthropometryIds, list)
    fun submitDiagnosisBenIds(list: List<Long>)   = applyIdList(diagnosisIds, list)


    class BenClickListener(
        private val clickedBen: (item: BenBasicDomain, hhId: Long, benId: Long, relToHeadId: Int) -> Unit,
        private val clickedWifeBen: (item: BenBasicDomain, hhId: Long, benId: Long, relToHeadId: Int) -> Unit,
        private val clickedHusbandBen: (item: BenBasicDomain, hhId: Long, benId: Long, relToHeadId: Int) -> Unit,
        private val clickedChildben: (item: BenBasicDomain, hhId: Long, benId: Long, relToHeadId: Int) -> Unit,
        private val clickedHousehold: (item: BenBasicDomain, hhId: Long) -> Unit,
        private val clickedABHA: (item: BenBasicDomain, benId: Long, hhId: Long) -> Unit,
        private val clickedAddAllBenBtn: (item: BenBasicDomain, benId: Long, hhId: Long, isViewMode: Boolean, isIFA: Boolean) -> Unit,
        private val callBen: (ben: BenBasicDomain) -> Unit,
        private val softDeleteBen: (ben: BenBasicDomain) -> Unit,
        private val clickedVitalScreen: (item: BenBasicDomain, benId: Long, hhId: Long) -> Unit = { _, _, _ -> },
        private val clickedResult: (item: BenBasicDomain, benId: Long, hhId: Long) -> Unit = { _, _, _ -> },
        private val clickedOrderAction: (item: BenBasicDomain, action: String, orderType: String) -> Unit = { _, _, _ -> },
        private val clickedGeneralOpd: (item: BenBasicDomain, benId: Long, hhId: Long, viewOnly: Boolean) -> Unit = { _, _, _, _ -> },
        private val clickedAnthropometry: (item: BenBasicDomain, benId: Long, hhId: Long, viewOnly: Boolean) -> Unit = { _, _, _, _ -> },
        private val clickedExamine: (item: BenBasicDomain, benId: Long) -> Unit = { _, _ -> },
        private val clickedNonHHHousehold: (item: BenBasicDomain) -> Unit = {}
    ) {
        fun onClickedBen(item: BenBasicDomain) = clickedBen(
            item,
            item.hhId,
            item.benId,
            item.relToHeadId - 1
        )


        fun onClickedWifeBen(item: BenBasicDomain) = clickedWifeBen(
            item,
            item.hhId,
            item.benId,
            item.relToHeadId
        )


        fun onClickedHusbandBen(item: BenBasicDomain) = clickedHusbandBen(
            item,
            item.hhId,
            item.benId,
            item.relToHeadId
        )

        fun onClickChildBen(item: BenBasicDomain) = clickedChildben(
            item,
            item.hhId,
            item.benId,
            item.relToHeadId
        )

        fun onClickedHouseHold(item: BenBasicDomain) = clickedHousehold(item, item.hhId)
        fun onClickABHA(item: BenBasicDomain) = clickedABHA(item, item.benId, item.hhId)
        fun clickedAddAllBenBtn(item: BenBasicDomain, isMatched: Boolean, isIFA: Boolean) =
            clickedAddAllBenBtn(item, item.benId, item.hhId, isMatched, isIFA)
        fun onClickVitalScreen(item: BenBasicDomain) =
            clickedVitalScreen(item, item.benId, item.hhId)
        fun onClickResult(item: BenBasicDomain) =
            clickedResult(item, item.benId, item.hhId)
        fun onClickOrderAction(item: BenBasicDomain, action: String, orderType: String) =
            clickedOrderAction(item, action, orderType)
        fun onClickGeneralOpd(item: BenBasicDomain, viewOnly: Boolean) =
            clickedGeneralOpd(item, item.benId, item.hhId, viewOnly)
        fun onClickAnthropometry(item: BenBasicDomain, viewOnly: Boolean) =
            clickedAnthropometry(item, item.benId, item.hhId, viewOnly)

        fun onClickedForCall(item: BenBasicDomain) = callBen(item)
        fun onClickSoftDeleteBen(item: BenBasicDomain) = softDeleteBen(item)
        fun onClickExamine(item: BenBasicDomain) = clickedExamine(item, item.benId)
        fun onClickNonHHHousehold(item: BenBasicDomain) = clickedNonHHHousehold(item)
    }
}
