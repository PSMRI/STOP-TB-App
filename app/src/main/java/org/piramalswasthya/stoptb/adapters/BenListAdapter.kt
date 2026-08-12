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
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemBenBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.ui.setSyncStateForBen
import timber.log.Timber

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
            unsyncedVitalBenIds: List<Long> = emptyList(),
            unsyncedTbScreeningBenIds: List<Long> = emptyList(),
            unsyncedGeneralOpdBenIds: List<Long> = emptyList(),
            syncingVitalBenIds: List<Long> = emptyList(),
            syncingTbScreeningBenIds: List<Long> = emptyList(),
            syncingGeneralOpdBenIds: List<Long> = emptyList(),
            tbSuspectedBenIds: List<Long> = emptyList(),
            contactFollowUpDoneBenIds: List<Long> = emptyList(),
            tptFollowUpDoneBenIds: List<Long> = emptyList(),
            tptEligibleBenIds: List<Long> = emptyList(),
            childCountMap: Map<Long, Int> = emptyMap(),
            showActionButtons: Boolean = true,
            showResultButton: Boolean = false,
            showAnthropometryButton: Boolean = false,
            showExamineButton: Boolean = true,
            tbDiagnosticsList: List<TBDiagnosticsCache> = emptyList(),
            source: Int = 0,
            retryingBenIds: List<Long> = emptyList()
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
            val hasUnsyncedVital = unsyncedVitalBenIds.contains(item.benId)
            val hasUnsyncedTbScreening = unsyncedTbScreeningBenIds.contains(item.benId)
            val hasUnsyncedGeneralOpd = unsyncedGeneralOpdBenIds.contains(item.benId)
            val hasSyncingVital = syncingVitalBenIds.contains(item.benId)
            val hasSyncingTbScreening = syncingTbScreeningBenIds.contains(item.benId)
            val hasSyncingGeneralOpd = syncingGeneralOpdBenIds.contains(item.benId)
            val hasDiagnosis = tbSuspectedBenIds.contains(item.benId)
            val hasContactFollowUpDone = contactFollowUpDoneBenIds.contains(item.benId)
            val hasTptFollowUpDone = tptFollowUpDoneBenIds.contains(item.benId)
            val isTptEligible = tptEligibleBenIds.contains(item.benId)
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
                    val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
                    val isCounsellingOfficer = pref?.getLoggedInUser()?.role.isCounsellingOfficerRole()
                    val canActOnReferral = isNurse || isCounsellingOfficer
                    val config = when (source) {
                        6 -> {
                            val status = tbDiag?.xrayOrderStatus
                            val referred = tbDiag?.isReferredForDigitalChestXray

                            when {
                                status.equals("REFUSED", ignoreCase = true) || referred == false -> {
                                    ButtonConfig("TEST REFUSED", android.R.color.darker_gray, "NONE", "XRAY_CHEST")
                                }
                                status.equals("COMPLETED", ignoreCase = true) -> {
                                    ButtonConfig("VIEW RESULT", android.R.color.holo_green_dark, "VIEW", "XRAY_CHEST")
                                }
                                status.equals("FAILED", ignoreCase = true) -> {
                                    ButtonConfig("Retry Referral", android.R.color.holo_red_dark, "RETRY_PUSH", "XRAY_CHEST")
                                }
                                status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) || status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PENDING", ignoreCase = true) -> {
                                    ButtonConfig("Pending", android.R.color.darker_gray, "NONE", "XRAY_CHEST")
                                }
                                status.equals("POLLING_TIMEOUT", ignoreCase = true) || status.equals("MANUAL_ENTRY", ignoreCase = true) -> {
                                    ButtonConfig("Pending", android.R.color.holo_orange_dark, "COMPLETE", "XRAY_CHEST")
                                }
                                else -> {
                                    ButtonConfig("Pending", android.R.color.holo_orange_dark, "COMPLETE", "XRAY_CHEST")
                                }
                            }
                        }
                        7 -> {
                            val status = tbDiag?.trueNatOrderStatus
                            val sputumCollected = tbDiag?.isSputumCollected
                            val naatRes = tbDiag?.naatResult
                            val rifStatus = tbDiag?.rifOrderStatus
                            val isHubConnected = pref?.isCampHubConnected() == true
                            val isTruenatDevIntegrated = pref?.getTruenatIntegrated() == true
                            val isTruenatManual = !isTruenatDevIntegrated || !isHubConnected
                            
                            binding.btnVitalScreenSecondary.visibility = View.GONE
                            
                            when {
                                status.equals("REFUSED", ignoreCase = true) || sputumCollected == false -> {
                                    ButtonConfig("TEST REFUSED", android.R.color.darker_gray, "NONE", "SPUTUM_TRUENAT")
                                }
                                status.equals("COMPLETED", ignoreCase = true) -> {
                                    val isMtbDetected = !naatRes.isNullOrBlank() && {
                                        val clean = naatRes.trim().lowercase()
                                        (clean.contains("positive") || clean.contains("detected")) && !clean.contains("not") && !clean.contains("negative")
                                    }()
                                    val conf = ButtonConfig(if (isMtbDetected) "VIEW MTB RESULT" else "VIEW RESULT", android.R.color.holo_green_dark, "VIEW", "SPUTUM_TRUENAT")
                                    if (isMtbDetected) {
                                        binding.btnVitalScreenSecondary.visibility = View.VISIBLE
                                        
                                        if (!isTruenatManual) {
                                            when {
                                                rifStatus == null || rifStatus.equals("PENDING", ignoreCase = true) || rifStatus.equals("CREATED", ignoreCase = true) || rifStatus.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) || rifStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) || rifStatus.equals("IN_PROGRESS", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "Pending"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.darker_gray))
                                                    binding.btnVitalScreenSecondary.isEnabled = false
                                                    binding.btnVitalScreenSecondary.alpha = 0.5f
                                                }
                                                rifStatus.equals("FAILED", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "Retry Referral"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_red_dark))
                                                    binding.btnVitalScreenSecondary.isEnabled = canActOnReferral && !retryingBenIds.contains(item.benId)
                                                    binding.btnVitalScreenSecondary.alpha = if (canActOnReferral && !retryingBenIds.contains(item.benId)) 1.0f else 0.5f
                                                    binding.btnVitalScreenSecondary.setOnClickListener {
                                                        clickListener?.onClickOrderAction(item, "RETRY_PUSH", "MDR_RIF")
                                                    }
                                                }
                                                rifStatus.equals("POLLING_TIMEOUT", ignoreCase = true) || rifStatus.equals("MANUAL_ENTRY", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "Pending"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_orange_dark))
                                                    binding.btnVitalScreenSecondary.isEnabled = canActOnReferral
                                                    binding.btnVitalScreenSecondary.alpha = if (canActOnReferral) 1.0f else 0.5f
                                                    binding.btnVitalScreenSecondary.setOnClickListener {
                                                        clickListener?.onClickOrderAction(item, "COMPLETE_RIF", "MDR_RIF")
                                                    }
                                                }
                                                rifStatus.equals("COMPLETED", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "VIEW RIF RESULT"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_green_dark))
                                                    binding.btnVitalScreenSecondary.isEnabled = true
                                                    binding.btnVitalScreenSecondary.alpha = 1.0f
                                                    binding.btnVitalScreenSecondary.setOnClickListener {
                                                        clickListener?.onClickOrderAction(item, "VIEW_RIF", "MDR_RIF")
                                                    }
                                                }
                                                else -> {
                                                    binding.btnVitalScreenSecondary.visibility = View.GONE
                                                }
                                            }
                                        } else {
                                            when {
                                                rifStatus == null || rifStatus.equals("PENDING", ignoreCase = true) || rifStatus.equals("CREATED", ignoreCase = true) || rifStatus.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) || rifStatus.equals("MANUAL_ENTRY", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "Pending"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_orange_dark))
                                                    binding.btnVitalScreenSecondary.isEnabled = canActOnReferral
                                                    binding.btnVitalScreenSecondary.alpha = if (canActOnReferral) 1.0f else 0.5f
                                                    binding.btnVitalScreenSecondary.setOnClickListener {
                                                        clickListener?.onClickOrderAction(item, "COMPLETE_RIF", "MDR_RIF")
                                                    }
                                                }
                                                rifStatus.equals("COMPLETED", ignoreCase = true) -> {
                                                    binding.btnVitalScreenSecondary.text = "VIEW RIF RESULT"
                                                    binding.btnVitalScreenSecondary.setBackgroundTintList(ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_green_dark))
                                                    binding.btnVitalScreenSecondary.isEnabled = true
                                                    binding.btnVitalScreenSecondary.alpha = 1.0f
                                                    binding.btnVitalScreenSecondary.setOnClickListener {
                                                        clickListener?.onClickOrderAction(item, "VIEW_RIF", "MDR_RIF")
                                                    }
                                                }
                                                else -> {
                                                    binding.btnVitalScreenSecondary.visibility = View.GONE
                                                }
                                            }
                                        }
                                    }
                                    conf
                                }
                                status.equals("FAILED", ignoreCase = true) -> {
                                    ButtonConfig("Retry Referral", android.R.color.holo_red_dark, "RETRY_PUSH", "SPUTUM_TRUENAT")
                                }
                                status.equals("POLLING_TIMEOUT", ignoreCase = true) || status.equals("MANUAL_ENTRY", ignoreCase = true) -> {
                                    ButtonConfig("Pending", android.R.color.holo_orange_dark, "COMPLETE", "SPUTUM_TRUENAT")
                                }
                                status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) || status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PENDING", ignoreCase = true) -> {
                                    ButtonConfig("Pending", android.R.color.darker_gray, "NONE", "SPUTUM_TRUENAT")
                                }
                                else -> {
                                     ButtonConfig("Pending", android.R.color.holo_orange_dark, "COMPLETE", "SPUTUM_TRUENAT")
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

                    val isViewAction = config.action == "VIEW" || config.action == "VIEW_LC" || config.action == "ENTER_LC"
                    if (config.action == "POLL" || config.action == "NO_RESULT" || config.action == "NONE") {
                        binding.btnVitalScreen.isEnabled = false
                        binding.btnVitalScreen.alpha = 0.5f
                    } else if (!canActOnReferral && !isViewAction) {
                        binding.btnVitalScreen.isEnabled = false
                        binding.btnVitalScreen.alpha = 0.5f
                    } else {
                        binding.btnVitalScreen.isEnabled = true
                        binding.btnVitalScreen.alpha = 1.0f
                    }

                    val isRetryPushInFlight = config.action == "RETRY_PUSH" && retryingBenIds.contains(item.benId)
                    if (isRetryPushInFlight) {
                        binding.btnVitalScreen.isEnabled = false
                        binding.btnVitalScreen.alpha = 1.0f
                        binding.btnVitalScreen.icon = androidx.swiperefreshlayout.widget.CircularProgressDrawable(binding.root.context).apply {
                            setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
                            setColorSchemeColors(android.graphics.Color.WHITE)
                            start()
                        }
                        binding.btnVitalScreen.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    } else {
                        (binding.btnVitalScreen.icon as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                        binding.btnVitalScreen.icon = null
                    }

                    val isSecondaryRetryInFlight = binding.btnVitalScreenSecondary.visibility == View.VISIBLE &&
                            binding.btnVitalScreenSecondary.text == "Retry Referral" &&
                            retryingBenIds.contains(item.benId)
                    if (isSecondaryRetryInFlight) {
                        binding.btnVitalScreenSecondary.isEnabled = false
                        binding.btnVitalScreenSecondary.alpha = 1.0f
                        binding.btnVitalScreenSecondary.icon = androidx.swiperefreshlayout.widget.CircularProgressDrawable(binding.root.context).apply {
                            setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
                            setColorSchemeColors(android.graphics.Color.WHITE)
                            start()
                        }
                        binding.btnVitalScreenSecondary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    } else {
                        (binding.btnVitalScreenSecondary.icon as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                        binding.btnVitalScreenSecondary.icon = null
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
                            val isXrayDone = tbDiag?.isChestXRayDone
                            when {
                                referred == false || status.equals("REFUSED", ignoreCase = true) || isXrayDone == false -> {
                                    val reasonStr = if (referred == false) {
                                        formatDenialReason(tbDiag?.reasonForDenialChestXray, tbDiag?.reasonForDenialChestXrayOther)
                                    } else {
                                        formatDenialReason(tbDiag?.reasonNotConductedChestXray, tbDiag?.reasonNotConductedChestXrayOther)
                                    }
                                    if (reasonStr.isNotBlank()) {
                                        "Referral Status: Declined / Not Conducted\nReason for Refusal:\n$reasonStr"
                                    } else {
                                        "Referral Status: Declined / Not Conducted"
                                    }
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
                                            val rawRes = tbDiag?.chestXRayResult
                                            val formattedRes = when {
                                                rawRes.isNullOrBlank() -> "Available"
                                                rawRes.equals("Positive", ignoreCase = true) || rawRes.equals("TB Presumptive", ignoreCase = true) -> "TB Presumptive"
                                                rawRes.equals("Negative", ignoreCase = true) || rawRes.equals("Normal", ignoreCase = true) -> "Normal"
                                                else -> rawRes
                                            }
                                            "Referral Status: Completed\nChest X-ray Result: $formattedRes"
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
                            val naatRes = tbDiag?.naatResult
                            val rifStatus = tbDiag?.rifOrderStatus
                            val rifRes = tbDiag?.trueNatRifResult
                            val isNaatConducted = tbDiag?.isNaatConducted
                            when {
                                sputumCollected == false || status.equals("REFUSED", ignoreCase = true) || isNaatConducted == false -> {
                                    val reasonStr = if (sputumCollected == false) {
                                        formatDenialReason(tbDiag?.reasonForDenialSputum, tbDiag?.reasonForDenialSputumOther)
                                    } else {
                                        formatDenialReason(tbDiag?.reasonNotConductedNaat, tbDiag?.reasonNotConductedNaatOther)
                                    }
                                    if (reasonStr.isNotBlank()) {
                                        "Status: Declined / Not Conducted\nReason for Refusal:\n$reasonStr"
                                    } else {
                                        "Status: Declined / Not Conducted"
                                    }
                                }
                                sputumCollected == true -> {
                                    when {
                                        status.equals("PENDING", ignoreCase = true) || status.equals("CREATED", ignoreCase = true) || status.equals("AWAITING_TEST_COMPLETION", ignoreCase = true) -> {
                                            "Status: Referred for TrueNat\nMTB Order Status: Awaiting Test Completion"
                                        }
                                        status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("PROCESSING", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                            "Status: Fetching TrueNat Result...\nMTB Order Status: Awaiting Provider Result"
                                        }
                                        status.equals("COMPLETED", ignoreCase = true) -> {
                                            val formattedMtb = when {
                                                naatRes.isNullOrBlank() -> "Available"
                                                naatRes.equals("TB Positive", ignoreCase = true) || naatRes.equals("MTB detected", ignoreCase = true) -> "MTB Detected"
                                                naatRes.equals("TB Negative", ignoreCase = true) || naatRes.equals("MTB not detected", ignoreCase = true) -> "MTB Not Detected"
                                                else -> naatRes
                                            }
                                            val isMtbDetected = naatRes.equals("MTB detected", ignoreCase = true) || naatRes.equals("TB Positive", ignoreCase = true)
                                            if (isMtbDetected) {
                                                val formattedRif = when {
                                                    rifRes.isNullOrBlank() -> null
                                                    rifRes.equals("DR TB", ignoreCase = true) || rifRes.equals("Rif Resistance Detected", ignoreCase = true) -> "RIF Resistance Detected"
                                                    rifRes.equals("Non DR TB", ignoreCase = true) || rifRes.equals("Rif Resistance Not Detected", ignoreCase = true) -> "RIF Resistance Not Detected"
                                                    else -> rifRes
                                                }
                                                when {
                                                    rifStatus == null || rifStatus.equals("PENDING", ignoreCase = true) || rifStatus.equals("CREATED", ignoreCase = true) -> {
                                                        "Status: Result Available\nMTB: $formattedMtb\nRIF Order: Created (Awaiting Completion)"
                                                    }
                                                    rifStatus.equals("IN_PROGRESS", ignoreCase = true) || rifStatus.equals("PROCESSING", ignoreCase = true) || rifStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) -> {
                                                        "Status: Fetching RIF Result...\nMTB: $formattedMtb\nRIF Order: Awaiting Provider Result"
                                                    }
                                                    rifStatus.equals("COMPLETED", ignoreCase = true) -> {
                                                        "Status: Result Available\nMTB: $formattedMtb\nRIF: ${formattedRif ?: "Available"}"
                                                    }
                                                    rifStatus.equals("FAILED", ignoreCase = true) -> {
                                                        "Status: Result Available\nMTB: $formattedMtb\nRIF: Sync Failed (Retry Required)"
                                                    }
                                                    else -> {
                                                        "Status: Result Available\nMTB: $formattedMtb"
                                                    }
                                                }
                                            } else {
                                                "Status: Result Available\nMTB: $formattedMtb"
                                            }
                                        }
                                        status.equals("FAILED", ignoreCase = true) -> {
                                            "Status: Referred for TrueNat"
                                        }
                                        else -> "Status: Referred for TrueNat"
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
            // Counselling Officer: TB Screening + Contact Follow Up are always required (total = 2);
            // TPT Follow Up becomes a 3rd required item (total = 3) only when this beneficiary's
            // ClinicalScreeningStatus answer is TPT_ELIGIBLE (see IContactTracingRepository.observeTptEligibleBenIds) —
            // otherwise FULL_TREATMENT/NO_TREATMENT beneficiaries would incorrectly get stuck at x/3.
            // Others: all 5 forms
            val currentRole = pref?.getLoggedInUser()?.role
            val isCounsellingOfficer = currentRole.isCounsellingOfficerRole()
            val isRegistrar = pref?.getLoggedInUser()?.role.isRegistrationOfficerRole()
            val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
            val isCounsellingOfficerForExamine = pref?.getLoggedInUser()?.role.isCounsellingOfficerRole()
            val (examineFilledCount, examineTotal) = if (isCounsellingOfficerForExamine) {
                val requiredItems = if (isTptEligible) {
                    listOf(hasTbScreening, hasContactFollowUpDone, hasTptFollowUpDone)
                } else {
                    listOf(hasTbScreening, hasContactFollowUpDone)
                }
                Pair(requiredItems.count { it }, requiredItems.size)
            } else if (isRegistrar) {
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

            val relevantUnsynced = if (isRegistrar) {
                hasUnsyncedTbScreening
            } else if (isNurse || isCounsellingOfficer) {
                hasUnsyncedVital || hasUnsyncedTbScreening || hasUnsyncedGeneralOpd
            } else {
                false
            }
            val relevantSyncing = if (isRegistrar) {
                hasSyncingTbScreening
            } else if (isNurse || isCounsellingOfficer) {
                hasSyncingVital || hasSyncingTbScreening || hasSyncingGeneralOpd
            } else {
                false
            }
            val effectiveSyncState = if (showSyncIcon && !item.isDeath && !item.isDeactivate) {
                when {
                    relevantUnsynced -> SyncState.UNSYNCED
                    relevantSyncing -> SyncState.SYNCING
                    else -> item.syncState
                }
            } else {
                null
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
            binding.ivSyncState.setSyncStateForBen(effectiveSyncState)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = BenViewHolder.from(parent)

    private val benIds            = mutableListOf<Long>()
    private val tbScreeningIds    = mutableListOf<Long>()
    private val generalOpdIds     = mutableListOf<Long>()
    private val anthropometryIds  = mutableListOf<Long>()
    private val unsyncedVitalIds = mutableListOf<Long>()
    private val unsyncedTbScreeningIds = mutableListOf<Long>()
    private val unsyncedGeneralOpdIds = mutableListOf<Long>()
    private val syncingVitalIds = mutableListOf<Long>()
    private val syncingTbScreeningIds = mutableListOf<Long>()
    private val syncingGeneralOpdIds = mutableListOf<Long>()
    private val diagnosisIds      = mutableListOf<Long>()
    private val contactFollowUpDoneIds = mutableListOf<Long>()
    private val tptFollowUpDoneIds     = mutableListOf<Long>()
    private val tptEligibleIds         = mutableListOf<Long>()
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
            unsyncedVitalIds,
            unsyncedTbScreeningIds,
            unsyncedGeneralOpdIds,
            syncingVitalIds,
            syncingTbScreeningIds,
            syncingGeneralOpdIds,
            diagnosisIds,
            contactFollowUpDoneIds,
            tptFollowUpDoneIds,
            tptEligibleIds,
            showActionButtons = showActionButtons,
            showResultButton = showResultButton,
            showAnthropometryButton = showAnthropometryButton,
            tbDiagnosticsList = tbDiagnosticsList,
            source = source,
            showExamineButton = showExamineButton
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
    fun submitUnsyncedVitalBenIds(list: List<Long>) = applyIdList(unsyncedVitalIds, list)
    fun submitUnsyncedTbScreeningBenIds(list: List<Long>) = applyIdList(unsyncedTbScreeningIds, list)
    fun submitUnsyncedGeneralOpdBenIds(list: List<Long>) = applyIdList(unsyncedGeneralOpdIds, list)
    fun submitSyncingVitalBenIds(list: List<Long>) = applyIdList(syncingVitalIds, list)
    fun submitSyncingTbScreeningBenIds(list: List<Long>) = applyIdList(syncingTbScreeningIds, list)
    fun submitSyncingGeneralOpdBenIds(list: List<Long>) = applyIdList(syncingGeneralOpdIds, list)
    fun submitDiagnosisBenIds(list: List<Long>)   = applyIdList(diagnosisIds, list)
    fun submitContactFollowUpDoneBenIds(list: List<Long>) = applyIdList(contactFollowUpDoneIds, list)
    fun submitTptFollowUpDoneBenIds(list: List<Long>)      = applyIdList(tptFollowUpDoneIds, list)
    fun submitTptEligibleBenIds(list: List<Long>)          = applyIdList(tptEligibleIds, list)


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
