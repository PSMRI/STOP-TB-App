package org.piramalswasthya.stoptb.adapters

import android.content.Context
import org.piramalswasthya.stoptb.helpers.DiagnosticResultLabels
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R.attr.colorOnPrimary
import com.google.android.material.color.MaterialColors
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.configuration.TBScreeningDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemBenVitalActionBinding
import org.piramalswasthya.stoptb.databinding.RvItemBenAddSpouseBinding
import org.piramalswasthya.stoptb.databinding.RvItemBenClassicDetailsBinding
import org.piramalswasthya.stoptb.databinding.RvItemBenBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms
import org.piramalswasthya.stoptb.ui.setSyncStateForBen
import org.piramalswasthya.stoptb.ui.TbDiagnosticsSnapshot
import timber.log.Timber
import org.piramalswasthya.stoptb.model.DiagnosticOrderType
import org.piramalswasthya.stoptb.model.OrderAction

data class ButtonConfig(
    val text: String,
    val colorRes: Int,
    val action: OrderAction,
    val type: DiagnosticOrderType?
)

internal fun String?.isMissingOrderId(): Boolean =
    isNullOrBlank() || equals("N/A", ignoreCase = true)

// How long after a screening is saved a camp hub that creates referral orders may still be
// making them: it creates them as soon as the screening reaches it and re-checks every 2 minutes.
internal const val HUB_ORDER_CREATION_WINDOW_MS = 5 * 60_000L

/**
 * On a camp hub that creates referral orders, a PENDING with no order id is a local placeholder
 * the screening form wrote (before this tablet stopped writing it, or after a push whose failure
 * was never recorded), not an order the hub holds: the card treats it as "no order yet". A Retry
 * from there cannot duplicate an order, the hub returns any order it already has.
 */
internal fun String?.withoutHubPlaceholder(orderId: String?, pref: PreferenceDao?): String? =
    if (equals("PENDING", ignoreCase = true) && orderId.isMissingOrderId() &&
        pref?.isCampModeEnabled() == true && pref.hubCreatesReferralOrders()
    ) null else this

/**
 * Milliseconds left in which the camp hub is still expected to create a referral order for the
 * latest screening, or 0 when the tablet should offer Retry Referral instead.
 */
internal fun hubOrderWindowRemaining(tbDiag: TbDiagnosticsCardFields?, pref: PreferenceDao?): Long {
    if (pref == null || !pref.isCampModeEnabled() || !pref.hubCreatesReferralOrders()) return 0L
    val screenedAt = tbDiag?.latestScreeningAt ?: return 0L
    return (screenedAt + HUB_ORDER_CREATION_WINDOW_MS - System.currentTimeMillis())
        .coerceIn(0L, HUB_ORDER_CREATION_WINDOW_MS)
}

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
    private val roleManager: RoleManager? = null,
    private val showActionButtons: Boolean = true,
    private val showResultButton: Boolean = false,
    private val showAnthropometryButton: Boolean = false,
    private val showExamineButton: Boolean = true,
    private val showScreeningStatus: Boolean = false,
    private val showContactTracingForms: Boolean = false,
    private val showAddMemberButton: Boolean = false,
    private val showRedesignedCard: Boolean = false,
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

        // Rebinds this card when an "Order being created" window ends, so it moves on to Retry
        // Referral even if no data changes in the meantime. Posted on its own handler rather than
        // the view, so it can still be cancelled when the holder is rebound while detached.
        private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * The diagnostic order / result block is a ViewStub: only lists that show it build its ~40
         * views. [vital] builds it on first use; [vitalOrNull] is for resets, which have nothing
         * to reset while it was never built.
         */
        private val vitalOrNull: RvItemBenVitalActionBinding?
            get() = binding.vitalAction.binding as? RvItemBenVitalActionBinding
        private val addSpouseOrNull: RvItemBenAddSpouseBinding?
            get() = binding.addSpouseStub.binding as? RvItemBenAddSpouseBinding
        private val addSpouse: RvItemBenAddSpouseBinding
            get() {
                if (!binding.addSpouseStub.isInflated) binding.addSpouseStub.viewStub?.inflate()
                return binding.addSpouseStub.binding as RvItemBenAddSpouseBinding
            }
        private val vital: RvItemBenVitalActionBinding
            get() {
                if (!binding.vitalAction.isInflated) binding.vitalAction.viewStub?.inflate()
                return binding.vitalAction.binding as RvItemBenVitalActionBinding
            }
        private var hubOrderRefresh: Runnable? = null

        private fun scheduleHubOrderRefresh(delayMs: Long) {
            hubOrderRefresh?.let { refreshHandler.removeCallbacks(it) }
            val refresh = Runnable {
                hubOrderRefresh = null
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) bindingAdapter?.notifyItemChanged(position)
            }
            hubOrderRefresh = refresh
            refreshHandler.postDelayed(refresh, delayMs + 1_000L)
        }
        companion object {
            fun from(parent: ViewGroup): BenViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemBenBinding.inflate(layoutInflater, parent, false)
                return BenViewHolder(binding)
            }
        }

        private fun formatDenialReason(reason: String?, other: String?): String {
            if (reason.isNullOrBlank()) return ""
            return reason.split("|").joinToString("\n") { r ->
                if (r.equals("Other", ignoreCase = true) && !other.isNullOrBlank()) {
                    "Other: $other"
                } else {
                    r
                }
            }
        }

        private fun bindErrorMsg(text: String?) {
            if (text.isNullOrBlank()) {
                val block = vitalOrNull ?: return
                block.tvErrorMsg.visibility = View.GONE
                block.tvErrorMsg.movementMethod = null
                return
            }
            vital.tvErrorMsg.visibility = View.VISIBLE
            vital.tvErrorMsg.highlightColor = Color.TRANSPARENT
            vital.tvErrorMsg.movementMethod = LinkMovementMethod.getInstance()
            renderErrorMsg(text, expanded = false)
        }



        private fun renderErrorMsg(text: String, expanded: Boolean) {
            val prefix = binding.root.context.getString(R.string.error_message)
            val onPrimaryColor = MaterialColors.getColor(
                binding.root, colorOnPrimary, Color.WHITE
            )
            val isTruncated = !expanded && text.length > ERROR_MSG_COLLAPSED_BODY_LENGTH

            val builder = SpannableStringBuilder(prefix)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(onPrimaryColor),
                0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (expanded) {
                vital.tvErrorMsg.maxLines = Int.MAX_VALUE
                builder.append(text)
            } else {
                vital.tvErrorMsg.maxLines = 2
                builder.append(if (isTruncated) text.take(ERROR_MSG_COLLAPSED_BODY_LENGTH).trimEnd() + "…" else text)
            }

            if (expanded || isTruncated) {
                builder.append(" ")
                val toggleStart = builder.length
                builder.append(if (expanded) binding.root.context.getString(R.string.see_less) else binding.root.context.getString(R.string.see_more))
                builder.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            renderErrorMsg(text, expanded = !expanded)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = onPrimaryColor
                            ds.isFakeBoldText = true
                            ds.isUnderlineText = false
                        }
                    },
                    toggleStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            vital.tvErrorMsg.text = builder
        }

        private fun bindPillAndCaptions(
            status: String?,
            orderId: String?,
            benId: Long,
            testType: DiagnosticOrderType,
            pref: PreferenceDao?,
            isPendingManualResultSync: Boolean,
            pillContainer: View,
            pillIcon: android.widget.ImageView,
            pillText: android.widget.TextView,
            statusDivider: View,
            lastCheckedCaption: android.widget.TextView,
            pollingStoppedCaption: View,
            messageCaptionTitle: android.widget.TextView,
            messageCaptionText: android.widget.TextView,
            waitingText: String? = null
        ) {
            val ctx = binding.root.context

            fun setPillIcon(spinning: Boolean, staticRes: Int, tintRes: Int) {
                if (spinning) {
                    pillIcon.setImageDrawable(
                        androidx.swiperefreshlayout.widget.CircularProgressDrawable(ctx).apply {
                            setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
                            setColorSchemeColors(ContextCompat.getColor(ctx, tintRes))
                            start()
                        }
                    )
                    pillIcon.imageTintList = null
                } else {
                    (pillIcon.drawable as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                    pillIcon.setImageResource(staticRes)
                    pillIcon.imageTintList = ContextCompat.getColorStateList(ctx, tintRes)
                }
            }

            when {
                isPendingManualResultSync -> {
                    pillContainer.visibility = View.VISIBLE
                    setPillIcon(spinning = false, staticRes = R.drawable.ic_cloud_off_bottom_sheet, tintRes = android.R.color.darker_gray)
                    pillText.text = ctx.getString(R.string.order_status_saved_locally_syncing)
                    pillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
                    statusDivider.visibility = View.GONE
                    lastCheckedCaption.visibility = View.GONE
                    pollingStoppedCaption.visibility = View.GONE
                }
                status.equals(OrderStatus.PENDING.name, ignoreCase = true) && orderId.isMissingOrderId() -> {
                    pillContainer.visibility = View.VISIBLE
                    setPillIcon(spinning = false, staticRes = R.drawable.ic_exclamation_circle, tintRes = R.color.md_theme_light_error)
                    pillText.text = ctx.getString(R.string.order_status_order_not_created)
                    pillText.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_error))
                    statusDivider.visibility = View.GONE
                    lastCheckedCaption.visibility = View.GONE
                    pollingStoppedCaption.visibility = View.GONE
                }
                status.equals(OrderStatus.PENDING.name, ignoreCase = true) -> {
                    pillContainer.visibility = View.VISIBLE
                    val isHubDisconnected = pref?.isCampModeEnabled() == true && pref.isCampHubConnected() != true
                    if (isHubDisconnected) {
                        setPillIcon(spinning = false, staticRes = R.drawable.ic_block, tintRes = android.R.color.darker_gray)
                        pillText.text = ctx.getString(R.string.order_status_hub_disconnected)
                        pillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
                        statusDivider.visibility = View.GONE
                        lastCheckedCaption.visibility = View.GONE
                        pollingStoppedCaption.visibility = View.VISIBLE
                        messageCaptionTitle.text = ctx.getString(R.string.order_status_hub_disconnected_title)
                        messageCaptionText.text = ctx.getString(R.string.order_status_hub_disconnected_desc)
                    } else {
                        setPillIcon(spinning = true, staticRes = R.drawable.ic_health_person, tintRes = android.R.color.holo_orange_dark)
                        pillText.text = waitingText ?: ctx.getString(R.string.order_status_fetching_from_device)
                        pillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))

                        val lastChecked = pref?.getLastCheckedTime(benId, testType.wireValue) ?: 0L
                        if (pref?.isChangesFeedActive() == true) {
                            // Results arrive only through the changes feed, so there is no per-order check time to show.
                            statusDivider.visibility = View.VISIBLE
                            lastCheckedCaption.visibility = View.VISIBLE
                            lastCheckedCaption.text = ctx.getString(R.string.order_status_feed_auto_update)
                        } else if (lastChecked > 0L) {
                            statusDivider.visibility = View.VISIBLE
                            lastCheckedCaption.visibility = View.VISIBLE
                            lastCheckedCaption.text = ctx.getString(
                                R.string.order_status_last_checked_caption,
                                ctx.getString(R.string.order_status_few_seconds), 60
                            )
                        } else {
                            statusDivider.visibility = View.GONE
                            lastCheckedCaption.visibility = View.GONE
                        }
                        pollingStoppedCaption.visibility = View.GONE
                    }
                }
                // Invalid run ("Error-2"): the machine retests the same order; a manual result is still allowed.
                status.equals(OrderStatus.INVALID.name, ignoreCase = true) -> {
                    pillContainer.visibility = View.VISIBLE
                    setPillIcon(spinning = false, staticRes = R.drawable.ic_exclamation_circle, tintRes = android.R.color.holo_orange_dark)
                    pillText.text = ctx.getString(R.string.order_status_invalid_retest)
                    pillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
                    statusDivider.visibility = View.GONE
                    lastCheckedCaption.visibility = View.GONE
                    pollingStoppedCaption.visibility = View.GONE
                }
                status.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) -> {
                    pillContainer.visibility = View.VISIBLE
                    setPillIcon(spinning = false, staticRes = R.drawable.ic_health_person, tintRes = android.R.color.holo_blue_dark)
                    pillText.text = ctx.getString(R.string.order_status_manual_action_required)
                    pillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                    statusDivider.visibility = View.GONE
                    lastCheckedCaption.visibility = View.GONE
                    pollingStoppedCaption.visibility = View.VISIBLE
                    messageCaptionTitle.text = ctx.getString(R.string.order_status_polling_stopped_title)
                    messageCaptionText.text = ctx.getString(R.string.order_status_polling_stopped_desc)
                }
                else -> {
                    (pillIcon.drawable as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                    pillContainer.visibility = View.GONE
                    statusDivider.visibility = View.GONE
                    lastCheckedCaption.visibility = View.GONE
                    pollingStoppedCaption.visibility = View.GONE
                }
            }
        }
        private fun bindRifBlock(
            item: BenBasicDomain,
            tbDiag: TbDiagnosticsCardFields?,
            canActOnReferral: Boolean,
            pref: PreferenceDao?,
            retryingBenIds: Collection<Long>,
            clickListener: BenClickListener?
        ) {
            val ctx = binding.root.context
            vital.llRifBlock.visibility = View.VISIBLE
            val status = tbDiag?.rifOrderStatus

            // Reset per-bind (RecyclerView recycling).
            vital.btnRifPrimary.visibility = View.GONE
            vital.btnRifSecondary.visibility = View.GONE
            vital.llRifSummaryStrip.visibility = View.GONE
            vital.tvRifErrorMsg.visibility = View.GONE
            vital.tvRifOrderStatus.visibility = View.GONE
            vital.llRifOrderStatusPill.visibility = View.GONE
            vital.tvRifLastCheckedCaption.visibility = View.GONE
            vital.llRifPollingStoppedCaption.visibility = View.GONE
            vital.btnRifPrimary.icon = null
            vital.btnRifSecondary.icon = null

            val orderStatus = tbDiag?.trueNatOrderStatus
            val rifDisplay = DiagnosticResultLabels.rif(ctx, tbDiag?.trueNatRifResult)
            val rifReason = formatDenialReason(tbDiag?.reasonNotConductedRif, tbDiag?.reasonNotConductedRifOther)

            fun bindRifPill(pillStatus: String?, pendingSync: Boolean) = bindPillAndCaptions(
                pillStatus, tbDiag?.trueNatOrderId, item.benId, DiagnosticOrderType.SPUTUM_TRUENAT, pref, pendingSync,
                vital.llRifOrderStatusPill, vital.ivRifOrderStatusPillIcon, vital.tvRifOrderStatusPillText,
                vital.viewRifOrderStatusDivider, vital.tvRifLastCheckedCaption, vital.llRifPollingStoppedCaption,
                vital.tvRifPollingStoppedCaptionTitle, vital.tvRifPollingStoppedCaptionText,
                waitingText = ctx.getString(R.string.order_status_waiting_rif)
            )

            // RIF has no order of its own, so no Retry Referral / Create New Order here;
            // a new MTB order (main button) brings a new RIF.
            when {
                tbDiag?.rifManualResultPendingSync == true -> bindRifPill(orderStatus, pendingSync = true)
                rifDisplay != null -> {
                    vital.llRifSummaryStrip.visibility = View.VISIBLE
                    vital.tvRifSummaryText.text = withDeviceUpdateNote(
                        ctx.getString(R.string.order_status_rif_result, rifDisplay),
                        DiagnosticResultLabels.replacedByDevice(
                            "MDR_RIF", pref?.getManualEnteredResult(item.benId, "MDR_RIF"), tbDiag?.trueNatRifResult
                        )
                    )
                    vital.btnViewRifResult.setOnClickListener {
                        clickListener?.onClickOrderAction(item, OrderAction.VIEW_RIF, DiagnosticOrderType.MDR_RIF)
                    }
                }
                rifReason.isNotBlank() || status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> {
                    vital.tvRifOrderStatus.visibility = View.VISIBLE
                    vital.tvRifOrderStatus.text = if (rifReason.isNotBlank()) {
                        ctx.getString(R.string.order_status_rif_not_conducted, rifReason)
                    } else {
                        ctx.getString(R.string.order_status_rif_closed)
                    }
                }
                // MTB Detected was saved here but has not reached the server yet; RIF can only
                // be entered after it has.
                tbDiag?.trueNatManualResultPendingSync == true -> bindRifPill(orderStatus, pendingSync = true)
                // The vendor gave MTB Detected and completed the order without a RIF result
                // (its RIF report is still to come); the server takes no manual RIF on it.
                orderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ->
                    bindRifPill(OrderStatus.PENDING.name, pendingSync = false)
                // The vendor reported the RIF test FAILED. The server takes a hand-entered RIF on a
                // FAILED order (but no reasonToCloseRif), so only Enter RIF Result is offered.
                orderStatus.equals(OrderStatus.FAILED.name, ignoreCase = true) -> {
                    vital.tvRifErrorMsg.visibility = View.VISIBLE
                    vital.tvRifErrorMsg.text = ctx.getString(R.string.order_status_rif_failed) +
                            (tbDiag?.errorMsgTrueNat?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
                    vital.btnRifPrimary.visibility = View.VISIBLE
                    vital.btnRifPrimary.text = ctx.getString(R.string.order_status_enter_rif_result)
                    vital.btnRifPrimary.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_pencil)
                    vital.btnRifPrimary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    vital.btnRifPrimary.setBackgroundTintList(ContextCompat.getColorStateList(ctx, android.R.color.holo_red_dark))
                    vital.btnRifPrimary.isEnabled = canActOnReferral
                    vital.btnRifPrimary.alpha = if (canActOnReferral) 1.0f else 0.5f
                    vital.btnRifPrimary.setOnClickListener {
                        clickListener?.onClickOrderAction(item, OrderAction.COMPLETE_RIF, DiagnosticOrderType.MDR_RIF)
                    }
                }
                // PENDING (vendor running RIF), MANUAL_ENTRY (no vendor) or INVALID (RIF run was
                // invalid, the machine retests): RIF can be entered or closed by hand.
                orderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                        orderStatus.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) ||
                        orderStatus.equals(OrderStatus.INVALID.name, ignoreCase = true) -> {
                    bindRifPill(orderStatus, pendingSync = false)

                    vital.btnRifPrimary.visibility = View.VISIBLE
                    vital.btnRifPrimary.text = ctx.getString(R.string.order_status_enter_rif_result)
                    vital.btnRifPrimary.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_pencil)
                    vital.btnRifPrimary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    vital.btnRifPrimary.setBackgroundTintList(ContextCompat.getColorStateList(ctx, android.R.color.holo_red_dark))
                    vital.btnRifPrimary.isEnabled = canActOnReferral
                    vital.btnRifPrimary.alpha = if (canActOnReferral) 1.0f else 0.5f
                    vital.btnRifPrimary.setOnClickListener {
                        clickListener?.onClickOrderAction(item, OrderAction.COMPLETE_RIF, DiagnosticOrderType.MDR_RIF)
                    }

                    vital.btnRifSecondary.visibility = View.VISIBLE
                    vital.btnRifSecondary.text = ctx.getString(R.string.order_status_rif_not_conducted_button)
                    vital.btnRifSecondary.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_block)
                    vital.btnRifSecondary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    vital.btnRifSecondary.setBackgroundTintList(ContextCompat.getColorStateList(ctx, android.R.color.holo_orange_dark))
                    vital.btnRifSecondary.isEnabled = canActOnReferral
                    vital.btnRifSecondary.alpha = if (canActOnReferral) 1.0f else 0.5f
                    vital.btnRifSecondary.setOnClickListener {
                        clickListener?.onClickOrderAction(item, OrderAction.NOT_CONDUCTED, DiagnosticOrderType.MDR_RIF)
                    }
                }
                else -> Unit
            }
        }
        /** "MTB: MTB Detected · Updated by device" when a vendor result replaced the manual one. */
        private fun withDeviceUpdateNote(text: String, replaced: Boolean): String =
            if (replaced) binding.root.context.getString(R.string.order_status_updated_by_device_suffix, text) else text

        private fun bindOrderStatusExtras(
            item: BenBasicDomain,
            source: Int,
            tbDiag: TbDiagnosticsCardFields?,
            canActOnReferral: Boolean,
            pref: PreferenceDao?,
            retryingBenIds: Collection<Long>,
            clickListener: BenClickListener?
        ) {
            val ctx = binding.root.context
            vital.llOrderStatusPill.visibility = View.GONE
            vital.tvLastCheckedCaption.visibility = View.GONE
            vital.llPollingStoppedCaption.visibility = View.GONE
            vital.llMtbSummaryStrip.visibility = View.GONE
            vital.viewRifDivider.visibility = View.GONE
            vital.llRifBlock.visibility = View.GONE

            when (source) {
                6 -> {
                    val status = tbDiag?.xrayOrderStatus
                    bindPillAndCaptions(
                        status, tbDiag?.xrayOrderId, item.benId, DiagnosticOrderType.XRAY_CHEST, pref, tbDiag?.xrayManualResultPendingSync == true,
                        vital.llOrderStatusPill, vital.ivOrderStatusPillIcon, vital.tvOrderStatusPillText,
                        vital.viewOrderStatusDivider, vital.tvLastCheckedCaption, vital.llPollingStoppedCaption,
                        vital.tvPollingStoppedCaptionTitle, vital.tvPollingStoppedCaptionText
                    )
                    // A vendor X-ray result replaced the one entered by hand.
                    if (status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) &&
                        tbDiag?.xrayManualResultPendingSync != true &&
                        DiagnosticResultLabels.replacedByDevice(
                            "XRAY_CHEST", pref?.getManualEnteredResult(item.benId, "XRAY_CHEST"), tbDiag?.chestXRayResult
                        )
                    ) {
                        vital.llOrderStatusPill.visibility = View.VISIBLE
                        (vital.ivOrderStatusPillIcon.drawable as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                        vital.ivOrderStatusPillIcon.setImageResource(R.drawable.ic_health_person)
                        vital.ivOrderStatusPillIcon.imageTintList = ContextCompat.getColorStateList(ctx, android.R.color.holo_blue_dark)
                        vital.tvOrderStatusPillText.text = ctx.getString(
                            R.string.order_status_xray_updated_by_device, DiagnosticResultLabels.xray(tbDiag?.chestXRayResult)
                        )
                        vital.tvOrderStatusPillText.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_blue_dark))
                    }
                }
                7 -> {
                    val status = tbDiag?.trueNatOrderStatus
                    val isPendingTrueNatSync = tbDiag?.trueNatManualResultPendingSync == true
                    val mtbDisplay = DiagnosticResultLabels.mtb(ctx, tbDiag?.naatResult)
                    // The MTB result shows as soon as it is known, even while the order waits for RIF.
                    if (mtbDisplay != null && !isPendingTrueNatSync) {
                        vital.llMtbSummaryStrip.visibility = View.VISIBLE
                        vital.tvMtbSummaryText.text = withDeviceUpdateNote(
                            ctx.getString(R.string.order_status_mtb_result, mtbDisplay),
                            DiagnosticResultLabels.replacedByDevice(
                                "SPUTUM_TRUENAT", pref?.getManualEnteredResult(item.benId, "SPUTUM_TRUENAT"), tbDiag?.naatResult
                            )
                        )
                        vital.btnViewMtbResult.setOnClickListener {
                            clickListener?.onClickOrderAction(item, OrderAction.VIEW, DiagnosticOrderType.SPUTUM_TRUENAT)
                        }
                    } else {
                        bindPillAndCaptions(
                            status, tbDiag?.trueNatOrderId, item.benId, DiagnosticOrderType.SPUTUM_TRUENAT, pref, isPendingTrueNatSync,
                            vital.llOrderStatusPill, vital.ivOrderStatusPillIcon, vital.tvOrderStatusPillText,
                            vital.viewOrderStatusDivider, vital.tvLastCheckedCaption, vital.llPollingStoppedCaption,
                            vital.tvPollingStoppedCaptionTitle, vital.tvPollingStoppedCaptionText
                        )
                    }

                    // The RIF block's only condition: MTB Detected. A later server result that
                    // replaces it (e.g. a vendor MTB Not Detected) hides the block again on its own.
                    val isMtbPositive = MtbResult.fromResultText(tbDiag?.naatResult) == MtbResult.TB_POSITIVE
                    if (isMtbPositive) {
                        vital.viewRifDivider.visibility = View.VISIBLE
                        bindRifBlock(item, tbDiag, canActOnReferral, pref, retryingBenIds, clickListener)
                    }
                }
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
            benIdList: Collection<Long>,
            tbScreeningBenIds: Collection<Long> = emptyList(),
            generalOpdBenIds: Collection<Long> = emptyList(),
            anthropometryBenIds: Collection<Long> = emptyList(),
            unsyncedVitalBenIds: Collection<Long> = emptyList(),
            unsyncedTbScreeningBenIds: Collection<Long> = emptyList(),
            unsyncedGeneralOpdBenIds: Collection<Long> = emptyList(),
            syncingVitalBenIds: Collection<Long> = emptyList(),
            syncingTbScreeningBenIds: Collection<Long> = emptyList(),
            syncingGeneralOpdBenIds: Collection<Long> = emptyList(),
            tbSuspectedBenIds: Collection<Long> = emptyList(),
            contactFollowUpDoneBenIds: Collection<Long> = emptyList(),
            tptFollowUpDoneBenIds: Collection<Long> = emptyList(),
            tptEligibleBenIds: Collection<Long> = emptyList(),
            childCountMap: Map<Long, Int> = emptyMap(),
            tptRegistrationDoneBenIds: Collection<Long> = emptyList(),
            showActionButtons: Boolean = true,
            showResultButton: Boolean = false,
            showAnthropometryButton: Boolean = false,
            showExamineButton: Boolean = true,
            showScreeningStatus: Boolean = false,   // NEW
            tbDiagnosticsByBenId: Map<Long, TbDiagnosticsCardFields> = emptyMap(),
            // False until the decoration flow has delivered its first emission. The map
            // above starts empty, so without this a null row is indistinguishable from
            // "this beneficiary has no order" - see the loading guards below.
            tbDiagnosticsLoaded: Boolean = false,
            tbScreeningMap: Map<Long, TbScreeningSymptoms> = emptyMap(),
            source: Int = 0,
            retryingBenIds: Collection<Long> = emptyList(),
            showContactTracingForms: Boolean = false,
            roleManager: RoleManager? = null,
            showRedesignedCard: Boolean = false,
            householdMemberCountMap: Map<Long, Int> = emptyMap(),
            householdHeadNameMap: Map<Long, String> = emptyMap(),
            showAddMemberButton: Boolean = false
        ) {
            hubOrderRefresh?.let { refreshHandler.removeCallbacks(it) }
            hubOrderRefresh = null
            binding.btnAbha.visibility = View.VISIBLE
            if (!showSyncIcon) item.syncState = null
            binding.ben = item
            binding.clickListener = clickListener
            binding.showAbha = showAbha
            binding.showActionButtons = showActionButtons
            binding.showAddMemberButton = showAddMemberButton
            binding.showRegistrationDate = showRegistrationDate
            binding.hasAbha = !item.abhaId.isNullOrEmpty()
            binding.role = role

            val canCallBeneficiary = showCall && item.hasCallableMobileNo &&
                    !item.isDeath && !item.isDeactivate
            binding.tvRedesignMobileLabel.apply {
                val callIcon = if (canCallBeneficiary) {
                    ContextCompat.getDrawable(context, R.drawable.ic_call_icon)?.mutate()?.apply {
                        setTint(ContextCompat.getColor(context, android.R.color.white))
                        val iconSize = (16 * context.resources.displayMetrics.density).toInt()
                        setBounds(0, 0, iconSize, iconSize)
                    }
                } else {
                    null
                }
                setCompoundDrawablesRelative(null, null, callIcon, null)
                setOnClickListener(
                    if (canCallBeneficiary) View.OnClickListener {
                        clickListener?.onClickedForCall(item)
                    } else {
                        null
                    }
                )
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
            val hasTptRegistrationDone = tptRegistrationDoneBenIds.contains(item.benId)
            val isTptEligible = tptEligibleBenIds.contains(item.benId)
            binding.isGeneralOpdDone = hasGeneralOpd
            binding.isAnthropometryDone = hasAnthropometry

            val isNonHH = item.isNonHH
            val isHeadOfFamily = if (isNonHH) false else item.relToHeadId == 19
            val householdHeadName = householdHeadNameMap[item.hhId]
                ?.trim()
                ?.takeUnless { it.isBlank() }
                ?: item.familyHeadName
            val hasFamilyHeadName = !isNonHH && householdHeadName.isNotBlank() && householdHeadName != "Not Available"
            binding.tvAgeGender.visibility = if (showRedesignedCard) View.VISIBLE else View.GONE
            binding.tvAgeGender.text = "${item.gender.replaceFirstChar { it.titlecase() }} | ${compactAge(item.age)}"
            binding.llRedesignedDetails.visibility = if (showRedesignedCard) View.VISIBLE else View.GONE
            binding.ncdHofName.visibility = if (showRedesignedCard) View.GONE else View.VISIBLE
            // The classic details are a ViewStub: built only for lists using the classic card. The
            // variables reach it at executePendingBindings() below.
            if (!showRedesignedCard && !binding.classicDetails.isInflated) {
                binding.classicDetails.viewStub?.inflate()
            }
            binding.classicDetails.root?.visibility = if (showRedesignedCard) View.GONE else View.VISIBLE
            val classicDetails = binding.classicDetails.binding as? RvItemBenClassicDetailsBinding
            classicDetails?.registrationDate?.visibility =
                if (showRegistrationDate) View.VISIBLE else View.INVISIBLE
            binding.detailsDivider.visibility = if (showRedesignedCard) View.GONE else View.VISIBLE
            if (showRedesignedCard) {
                val fatherName = item.fatherName
                    ?.trim()
                    ?.takeUnless { it.isBlank() || it.equals("Not Available", ignoreCase = true) }
                val showSeparateFatherName = hasFamilyHeadName &&
                        !isHeadOfFamily &&
                        fatherName != null &&
                        normalizedName(householdHeadName) != normalizedName(fatherName)
                val relation = when {
                    isHeadOfFamily -> null
                    hasFamilyHeadName -> binding.root.context.getString(R.string.hof) to householdHeadName
                    !item.spouseName.isNullOrBlank() && item.spouseName != "Not Available" ->
                        binding.root.context.getString(R.string.tv_spouse_name_ph) to item.spouseName
                    else -> binding.root.context.getString(R.string.tv_father_name_ph) to
                        (item.fatherName?.takeIf { it.isNotBlank() } ?: "N/A")
                }
                binding.llRedesignRelation.visibility = if (relation == null) View.GONE else View.VISIBLE
                binding.tvRedesignRelationLabel.text = relation?.first
                binding.tvRedesignRelationValue.text = relation?.second
                binding.llRedesignFatherName.visibility = if (showSeparateFatherName) View.VISIBLE else View.GONE
                binding.tvRedesignFatherName.text = fatherName
                binding.tvRedesignRegistrationDate.text = item.regDate
                binding.llRedesignFamilyMembers.visibility = if (isNonHH) View.GONE else View.VISIBLE
                binding.tvRedesignFamilyMembers.text = (
                    householdMemberCountMap[item.hhId] ?: if (isNonHH) 1 else 0
                ).toString()
                binding.tvRedesignBeneficiaryId.text = item.benId.toString()
                binding.tvRedesignNikshayId.text = item.nikshayIdDisplay
                binding.tvRedesignMobileNumber.text = if (item.isPlaceholderMobileNo) "N/A" else item.mobileNoDisplay
            }
            classicDetails?.HOF?.visibility = View.GONE
            if (isNonHH) {
                binding.ivIsHead.visibility = View.VISIBLE
                binding.ivIsHead.setImageResource(R.drawable.icon_non_household)
                binding.ivIsHead.imageTintList = null
            } else {
                binding.ivIsHead.setImageResource(R.drawable.ic_icon_head_of_family)
                binding.ivIsHead.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
                binding.ivIsHead.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
            }
            binding.head.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
            binding.ncdHofName.visibility = if (!showRedesignedCard && !isHeadOfFamily && hasFamilyHeadName) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (showResultButton && !item.isDeath && !item.isDeactivate) {
                vital.btnVitalScreen.visibility = View.VISIBLE
            } else {
                vitalOrNull?.btnVitalScreen?.visibility = View.GONE
            }
            // Reset per bind to prevent recycled views from showing a previous row's error message.
            bindErrorMsg(null)
            vitalOrNull?.apply {
                llOrderStatusPill.visibility = View.GONE
                tvLastCheckedCaption.visibility = View.GONE
                llPollingStoppedCaption.visibility = View.GONE
                llMtbSummaryStrip.visibility = View.GONE
                viewRifDivider.visibility = View.GONE
                llRifBlock.visibility = View.GONE
            }

            binding.btnGeneralOpd.visibility = View.GONE
            binding.llGeneralOpdRow.visibility = View.GONE
            binding.llGeneralOpdAction.visibility = View.GONE

            binding.btnAnthropometry.visibility = View.GONE
            binding.llAnthropometryAction.visibility = View.GONE

            // Screening status infographic (Symptoms / X-Ray / TruNat) — Title → Icon → Status
            if (showScreeningStatus || item.isNonHH) {
                binding.detailsDivider.visibility = if (showRedesignedCard) View.VISIBLE else View.GONE
                binding.tvScreeningSectionTitle.visibility = View.GONE
                binding.llScreeningStatus.visibility = View.VISIBLE

                val tbDiagForStatus = tbDiagnosticsByBenId[item.benId]



                // ---------------------------------------------------------
                // 1. TB Symptoms — driven purely by DB: symptomsScreenedDate + screeningStatus
                // ---------------------------------------------------------
             //   val symptomsDone =TbScreeningUtils.getTbScreeningResult()
                val tbScreeningCache = tbScreeningMap[item.benId]
                val isPresumptive = TBScreeningDataset.TbScreeningUtils.isPresumptive(tbScreeningCache)

                binding.ivSymptoms.setImageResource(
                    if (hasTbScreening || tbScreeningCache != null) R.drawable.circle_check
                    else R.drawable.circle_uncheck
                )

                binding.tvSymptomsStatus.text = if (hasTbScreening) "Screened" else "Unscreened"
                binding.tvSymptomsStatus.visibility = View.GONE

                val context = binding.root.context

                val symptomsResultText = when (isPresumptive) {
                    true -> context.getString(R.string.screening_presumptive)
                    false -> context.getString(R.string.screening_asymptomatic)
                    null -> ""
                }

                binding.tvSymptomsResult.text = context.formatScreeningResult(symptomsResultText)
                binding.tvSymptomsResult.visibility =
                    if (showRedesignedCard) View.VISIBLE else View.GONE


                // ---------------------------------------------------------
                // 2. Chest X-Ray — driven purely by DB: chestXrayDoneDate + raw result
                // ---------------------------------------------------------
            //    val xrayDone = item.chestXrayDoneDate != null
                val xrayResult = tbDiagForStatus?.chestXRayResult
                val isPregnantFemale = item.gender.equals(Gender.FEMALE.name, ignoreCase = true) &&
                        item.reproductiveStatusId == 1
                val isNegativeXray = xrayResult?.contains("negative", ignoreCase = true) == true ||
                        xrayResult.equals("Normal", ignoreCase = true)

                binding.llXrayTile.visibility = if (isPregnantFemale) View.GONE else View.VISIBLE
                // Symptomatic beneficiaries get TrueNat whatever the X-ray result, so a Normal X-ray hides the
                // TrueNat tile only for an asymptomatic, unreferred beneficiary with no MTB order or result.
                val hasTrueNat = isPresumptive == true || isPregnantFemale ||
                        tbDiagForStatus?.isReferredForSputum == true ||
                        !tbDiagForStatus?.trueNatOrderId.isMissingOrderId() ||
                        !tbDiagForStatus?.trueNatOrderStatus.isNullOrBlank() ||
                        !tbDiagForStatus?.naatResult.isNullOrBlank()
                binding.llTruenatTile.visibility = if (isNegativeXray && !hasTrueNat) View.GONE else View.VISIBLE

                // Clinical Assessment tile removed from the card — the same referral criteria is
                // already surfaced via the dedicated Clinical Assessment line listing.
                binding.llClinicalAssessmentTile.visibility = View.GONE

                binding.llScreeningStatus.weightSum = listOf(
                    true, // TB Symptoms tile is always visible
                    !isPregnantFemale,
                    // Must match the TrueNat tile's visibility above, or a shown tile gets no width.
                    !isNegativeXray || hasTrueNat
                ).count { it }.toFloat()
                binding.ivXray.setImageResource(
                    if (!xrayResult.isNullOrBlank()) R.drawable.circle_check else R.drawable.circle_uncheck
                )
                binding.tvXrayStatus.text = if (xrayResult.isNullOrBlank()) "Unscreened" else "Screened"
                binding.tvXrayStatus.visibility = View.GONE
                binding.tvXrayResult.text = context.formatScreeningResult(xrayResult)
                binding.tvXrayResult.visibility = if (showRedesignedCard) View.VISIBLE else View.GONE

                // ---------------------------------------------------------
                // 3. TrueNat — driven purely by DB: trunatTestDoneDate + raw result
                // ---------------------------------------------------------
               // val truenatDone = item.trunatTestDoneDate != null
                val truenatResult = tbDiagForStatus?.naatResult
                binding.ivTruenat.setImageResource(
                    if (!truenatResult.isNullOrBlank()) R.drawable.circle_check else R.drawable.circle_uncheck
                )
                binding.tvTruenatStatus.text = if (truenatResult.isNullOrBlank()) "Unscreened" else "Screened"
                binding.tvTruenatStatus.visibility = View.GONE
                // The app's wording ("MTB Detected"), not the server's ("TB Positive").
                binding.tvTruenatResult.text = context.formatScreeningResult(
                    DiagnosticResultLabels.mtb(binding.root.context, truenatResult) ?: truenatResult
                )
                binding.tvTruenatResult.visibility = if (showRedesignedCard) View.VISIBLE else View.GONE
            } else {
                binding.detailsDivider.visibility = View.GONE
                binding.tvScreeningSectionTitle.visibility = View.GONE
                binding.llScreeningStatus.visibility = View.GONE
            }


            if (vitalOrNull?.btnVitalScreen?.visibility == View.VISIBLE) {
                if (showResultButton) {
                    val tbDiag = tbDiagnosticsByBenId[item.benId]
                    // Legacy, kept for reference:
//                    val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
//                    val isCounsellingOfficer = pref?.getLoggedInUser()?.role.isCounsellingOfficerRole()
//                    val canActOnReferral = isNurse || isCounsellingOfficer
                    val canActOnReferral = roleManager?.privilegesUnion()?.canActOnReferral == true
                    val hubOrderWait = hubOrderWindowRemaining(tbDiag, pref)
                    val config = when (source) {
                        6 -> {
                            val status = tbDiag?.xrayOrderStatus.withoutHubPlaceholder(tbDiag?.xrayOrderId, pref)
                            val referred = tbDiag?.isReferredForDigitalChestXray
                            vital.btnVitalScreenSecondary.visibility = View.GONE

                            when {
                                // The decoration map is filled by a separate flow and starts
                                // empty, so on every return to this list a null row here means
                                // "not loaded yet", not "no order". Falling through to the
                                // Retry Referral branch below made the button flicker through
                                // Retry Referral on each return - and that branch also fires
                                // CHECK_PUSH_ACTIVE and offers an action that raises a second
                                // order for a referral that already succeeded.
                                tbDiag == null && !tbDiagnosticsLoaded -> {
                                    ButtonConfig(context.getString(R.string.order_status_checking), android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.XRAY_CHEST)
                                }
                                referred == false -> {
                                    ButtonConfig("TEST REFUSED", android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.XRAY_CHEST)
                                }
                                status.equals(OrderStatus.FAILED.name, ignoreCase = true) -> {
                                    bindErrorMsg(tbDiag?.errorMsgXray)
                                    ButtonConfig(context.getString(R.string.order_status_retry_referral), android.R.color.holo_red_dark, OrderAction.RETRY_PUSH, DiagnosticOrderType.XRAY_CHEST)
                                }
                                status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) -> {
                                    ButtonConfig("VIEW RESULT", android.R.color.holo_green_dark, OrderAction.VIEW, DiagnosticOrderType.XRAY_CHEST)
                                }
                                status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> {
                                    ButtonConfig("Create New X-Ray Order", android.R.color.holo_blue_dark, OrderAction.REORDER, DiagnosticOrderType.XRAY_CHEST)
                                }
                                // See the note in bindRifBlock: a backend-reported status means
                                // the order exists, so a missing local id is "not fetched yet",
                                // not "referral failed".
                                // The camp hub creates this order from the screening; give it time
                                // before offering Retry Referral.
                                status.isNullOrBlank() && tbDiag?.xrayOrderId.isMissingOrderId() && hubOrderWait > 0 -> {
                                    scheduleHubOrderRefresh(hubOrderWait)
                                    ButtonConfig(context.getString(R.string.order_status_being_created), android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.XRAY_CHEST)
                                }
                                status.isNullOrBlank() && tbDiag?.xrayOrderId.isMissingOrderId() -> {
                                    clickListener?.onClickOrderAction(item, OrderAction.CHECK_PUSH_ACTIVE, DiagnosticOrderType.XRAY_CHEST)
                                    bindErrorMsg(tbDiag?.errorMsgXray)
                                    ButtonConfig(context.getString(R.string.order_status_retry_referral), android.R.color.holo_red_dark, OrderAction.RETRY_PUSH, DiagnosticOrderType.XRAY_CHEST)
                                }
                                else -> {
                                    vital.btnVitalScreenSecondary.visibility = View.VISIBLE
                                    vital.btnVitalScreenSecondary.text = context.getString(R.string.order_status_not_conducted)
                                    vital.btnVitalScreenSecondary.setBackgroundTintList(
                                        ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_orange_dark)
                                    )
                                    vital.btnVitalScreenSecondary.isEnabled = canActOnReferral
                                    vital.btnVitalScreenSecondary.alpha = if (canActOnReferral) 1.0f else 0.5f
                                    vital.btnVitalScreenSecondary.icon = ContextCompat.getDrawable(binding.root.context, R.drawable.ic_block)
                                    vital.btnVitalScreenSecondary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                                    vital.btnVitalScreenSecondary.setOnClickListener {
                                        clickListener?.onClickOrderAction(item, OrderAction.NOT_CONDUCTED, DiagnosticOrderType.XRAY_CHEST)
                                    }
                                    ButtonConfig(context.getString(R.string.order_status_enter_result), android.R.color.holo_red_dark, OrderAction.COMPLETE, DiagnosticOrderType.XRAY_CHEST)
                                }
                            }
                        }
                        7 -> {
                            val status = tbDiag?.trueNatOrderStatus.withoutHubPlaceholder(tbDiag?.trueNatOrderId, pref)
                            val referredForSputum = tbDiag?.isReferredForSputum
                            vital.btnVitalScreenSecondary.visibility = View.GONE
                            vital.btnVitalScreen.visibility = View.VISIBLE
                            vital.llMtbSummaryStrip.visibility = View.GONE

                            when {
                                // The decoration map is filled by a separate flow and starts
                                // empty, so on every return to this list a null row here means
                                // "not loaded yet", not "no order". Falling through to the
                                // Retry Referral branch below made the button flicker through
                                // Retry Referral on each return - and that branch also fires
                                // CHECK_PUSH_ACTIVE and offers an action that raises a second
                                // order for a referral that already succeeded.
                                tbDiag == null && !tbDiagnosticsLoaded -> {
                                    ButtonConfig(context.getString(R.string.order_status_checking), android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                referredForSputum == false -> {
                                    ButtonConfig("TEST REFUSED", android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                // A FAILED order whose MTB is already Detected failed in its RIF test:
                                // the RIF block offers Enter RIF Result instead.
                                status.equals(OrderStatus.FAILED.name, ignoreCase = true) &&
                                        MtbResult.fromResultText(tbDiag?.naatResult) != MtbResult.TB_POSITIVE -> {
                                    bindErrorMsg(tbDiag?.errorMsgTrueNat)
                                    ButtonConfig(context.getString(R.string.order_status_retry_referral), android.R.color.holo_red_dark, OrderAction.RETRY_PUSH, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                // Once MTB has a result (even only saved locally), the MTB part collapses to the strip
                                // and any RIF action is in the RIF block, even while the order still waits for RIF.
                                status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                                        (!tbDiag?.naatResult.isNullOrBlank() &&
                                                !status.equals(OrderStatus.CLOSED.name, ignoreCase = true)) -> {
                                    vital.btnVitalScreen.visibility = View.GONE
                                    ButtonConfig("", android.R.color.holo_green_dark, OrderAction.NONE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> {
                                    ButtonConfig("Create New Order", android.R.color.holo_blue_dark, OrderAction.REORDER, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                // See the note in bindRifBlock: a backend-reported status means
                                // the order exists, so a missing local id is "not fetched yet",
                                // not "referral failed".
                                status.isNullOrBlank() && tbDiag?.trueNatOrderId.isMissingOrderId() && hubOrderWait > 0 -> {
                                    scheduleHubOrderRefresh(hubOrderWait)
                                    ButtonConfig(context.getString(R.string.order_status_being_created), android.R.color.darker_gray, OrderAction.NONE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                status.isNullOrBlank() && tbDiag?.trueNatOrderId.isMissingOrderId() -> {
                                    clickListener?.onClickOrderAction(item, OrderAction.CHECK_PUSH_ACTIVE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                    bindErrorMsg(tbDiag?.errorMsgTrueNat)
                                    ButtonConfig(context.getString(R.string.order_status_retry_referral), android.R.color.holo_red_dark, OrderAction.RETRY_PUSH, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                                else -> {
                                    // PENDING / MANUAL_ENTRY (or no order status yet) —
                                    // same standing-actions pattern as Chest X-Ray.
                                    vital.btnVitalScreenSecondary.visibility = View.VISIBLE
                                    vital.btnVitalScreenSecondary.text = context.getString(R.string.order_status_not_conducted)
                                    vital.btnVitalScreenSecondary.setBackgroundTintList(
                                        ContextCompat.getColorStateList(binding.root.context, android.R.color.holo_orange_dark)
                                    )
                                    vital.btnVitalScreenSecondary.isEnabled = canActOnReferral
                                    vital.btnVitalScreenSecondary.alpha = if (canActOnReferral) 1.0f else 0.5f
                                    vital.btnVitalScreenSecondary.icon = ContextCompat.getDrawable(binding.root.context, R.drawable.ic_block)
                                    vital.btnVitalScreenSecondary.setOnClickListener {
                                        clickListener?.onClickOrderAction(item, OrderAction.NOT_CONDUCTED, DiagnosticOrderType.SPUTUM_TRUENAT)
                                    }
                                    ButtonConfig(context.getString(R.string.order_status_enter_result), android.R.color.holo_red_dark, OrderAction.COMPLETE, DiagnosticOrderType.SPUTUM_TRUENAT)
                                }
                            }
                        }
                        8 -> {
                            // Once submitted, this is view-only — results take 40-45 days and
                            // aren't meant to be re-edited from here, so no "/EDIT" in the label.
                            val hasLc = !tbDiag?.liquidCultureResult.isNullOrBlank()
                            if (hasLc) {
                                ButtonConfig("VIEW RESULT", android.R.color.holo_green_dark, OrderAction.VIEW_LC, DiagnosticOrderType.LIQUID_CULTURE)
                            } else {
                                ButtonConfig("ENTER RESULT", android.R.color.holo_red_dark, OrderAction.ENTER_LC, DiagnosticOrderType.LIQUID_CULTURE)
                            }
                        }
                        else -> ButtonConfig("RESULT", android.R.color.holo_green_dark, OrderAction.VIEW, null)
                    }

                    vital.btnVitalScreen.text = config.text
                    vital.btnVitalScreen.setBackgroundTintList(
                        ContextCompat.getColorStateList(binding.root.context, config.colorRes)
                    )
                    vital.btnVitalScreen.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )

                    val isViewAction = config.action == OrderAction.VIEW || config.action == OrderAction.VIEW_LC || config.action == OrderAction.ENTER_LC
                    if (config.action == OrderAction.POLL || config.action == OrderAction.NO_RESULT || config.action == OrderAction.NONE) {
                        vital.btnVitalScreen.isEnabled = false
                        vital.btnVitalScreen.alpha = 0.5f
                    } else if (!canActOnReferral && !isViewAction) {
                        vital.btnVitalScreen.isEnabled = false
                        vital.btnVitalScreen.alpha = 0.5f
                    } else {
                        vital.btnVitalScreen.isEnabled = true
                        vital.btnVitalScreen.alpha = 1.0f
                    }

                    val isRetryPushInFlight = (config.action == OrderAction.RETRY_PUSH || config.action == OrderAction.REORDER) &&
                            retryingBenIds.contains(item.benId)
                    if (isRetryPushInFlight) {
                        vital.btnVitalScreen.isEnabled = false
                        vital.btnVitalScreen.alpha = 1.0f
                        vital.btnVitalScreen.icon = androidx.swiperefreshlayout.widget.CircularProgressDrawable(binding.root.context).apply {
                            setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
                            setColorSchemeColors(android.graphics.Color.WHITE)
                            start()
                        }
                        vital.btnVitalScreen.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    } else {
                        (vital.btnVitalScreen.icon as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                        vital.btnVitalScreen.icon = if (config.action == OrderAction.COMPLETE)
                            ContextCompat.getDrawable(binding.root.context, R.drawable.ic_pencil) else null
                        vital.btnVitalScreen.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    }

                    val isSecondaryRetryInFlight = vital.btnVitalScreenSecondary.visibility == View.VISIBLE &&
                            vital.btnVitalScreenSecondary.text == "Retry Referral" &&
                            retryingBenIds.contains(item.benId)
                    if (isSecondaryRetryInFlight) {
                        vital.btnVitalScreenSecondary.isEnabled = false
                        vital.btnVitalScreenSecondary.alpha = 1.0f
                        vital.btnVitalScreenSecondary.icon = androidx.swiperefreshlayout.widget.CircularProgressDrawable(binding.root.context).apply {
                            setStyle(androidx.swiperefreshlayout.widget.CircularProgressDrawable.DEFAULT)
                            setColorSchemeColors(android.graphics.Color.WHITE)
                            start()
                        }
                        vital.btnVitalScreenSecondary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
                    } else {
                        (vital.btnVitalScreenSecondary.icon as? androidx.swiperefreshlayout.widget.CircularProgressDrawable)?.stop()
                        // "Mark Test Not Conducted" block icon was already set inline above for
                        // the standing-actions case (Chest X-Ray/MTB Pending) — don't clobber it.
                        if (vital.btnVitalScreenSecondary.text != context.getString(R.string.order_status_not_conducted)) {
                            vital.btnVitalScreenSecondary.icon = null
                        }
                    }

                    val statusText = when (source) {
                        6 -> {
                            val status = tbDiag?.xrayOrderStatus
                            val referred = tbDiag?.isReferredForDigitalChestXray
                            when {
                                referred == false -> {
                                    val reasonStr = formatDenialReason(tbDiag?.reasonForDenialChestXray, tbDiag?.reasonForDenialChestXrayOther)
                                    if (reasonStr.isNotBlank()) {
                                        "Referral Status: Declined / Not Conducted\nReason for Refusal: $reasonStr"
                                    } else {
                                        "Referral Status: Declined / Not Conducted"
                                    }
                                }
                                status.equals(OrderStatus.FAILED.name, ignoreCase = true) -> {
                                    null
                                }
                                status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) -> {
                                    null
                                }
                                status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> {
                                    val declineReasonStr = formatDenialReason(tbDiag?.reasonForDenialChestXray, tbDiag?.reasonForDenialChestXrayOther)
                                    val notConductedReasonStr = formatDenialReason(tbDiag?.reasonNotConductedChestXray, tbDiag?.reasonNotConductedChestXrayOther)
                                    when {
                                        declineReasonStr.isNotBlank() ->
                                            "Order Status: Closed — Declined\nReason: $declineReasonStr"
                                        notConductedReasonStr.isNotBlank() ->
                                            "Order Status: Closed — Not Conducted\nReason: $notConductedReasonStr"
                                        else ->
                                            "Order Status: Closed — Expired (Result not entered)"
                                    }
                                }
                                status.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) -> {
                                    null
                                }
                                status.equals(OrderStatus.PENDING.name, ignoreCase = true) -> {
                                    null
                                }
                                else -> null
                            }
                        }
                        7 -> {
                            val status = tbDiag?.trueNatOrderStatus
                            val referredForSputum = tbDiag?.isReferredForSputum
                            when {
                                referredForSputum == false -> {
                                    val reasonStr = formatDenialReason(tbDiag?.reasonForDenialSputum, tbDiag?.reasonForDenialSputumOther)
                                    if (reasonStr.isNotBlank()) {
                                        "Referral Status: Declined / Not Conducted\nReason for Refusal:$reasonStr"
                                    } else {
                                        "Referral Status: Declined / Not Conducted"
                                    }
                                }
                                status.equals(OrderStatus.FAILED.name, ignoreCase = true) -> {
                                    // Error message already surfaces via tv_error_msg.
                                    null
                                }
                                status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) -> {
                                    // Collapses to the compact summary strip instead.
                                    null
                                }
                                status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> {
                                    val declineReasonStr = formatDenialReason(tbDiag?.reasonForDenialSputum, tbDiag?.reasonForDenialSputumOther)
                                    val notConductedReasonStr = formatDenialReason(tbDiag?.reasonNotConductedNaat, tbDiag?.reasonNotConductedNaatOther)
                                    when {
                                        declineReasonStr.isNotBlank() ->
                                            "Order Status: Closed — Declined\nReason: $declineReasonStr"
                                        notConductedReasonStr.isNotBlank() ->
                                            "Order Status: Closed — Not Conducted\nReason: $notConductedReasonStr"
                                        // Closed after a result (MTB Invalid/Error, or RIF closed / invalid): the MTB strip and
                                        // the RIF block say why; it did not expire.
                                        !tbDiag?.naatResult.isNullOrBlank() -> "Order Status: Closed"
                                        else ->
                                            "Order Status: Closed — Expired (Result not entered)"
                                    }
                                }
                                status.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) -> {
                                    // Pill/banner below already conveys this — see the matching
                                    // Chest X-Ray comment above.
                                    null
                                }
                                status.equals(OrderStatus.PENDING.name, ignoreCase = true) -> {
                                    null
                                }
                                else -> null
                            }
                        }
                        else -> null
                    }

                    if (statusText != null) {
                        vital.tvOrderStatus.text = statusText
                        vital.tvOrderStatus.visibility = View.VISIBLE
                    } else {
                        vital.tvOrderStatus.visibility = View.GONE
                    }
                    val orderId = when (source) {
                        6 -> tbDiag?.xrayOrderId?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                        7 -> tbDiag?.trueNatOrderId?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                        else -> null
                    }
                    val hasOrderBeenPlaced = when (source) {
                        6 -> tbDiag?.isReferredForDigitalChestXray == true || !tbDiag?.xrayOrderStatus.isNullOrBlank()
                        7 -> tbDiag?.isReferredForSputum == true || !tbDiag?.trueNatOrderStatus.isNullOrBlank()
                        else -> false
                    }
                    val rifOrderId = if (source == 7) {
                        tbDiag?.rifOrderId?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                    } else null
                    val hasRifOrderBeenPlaced = source == 7 && !tbDiag?.rifOrderStatus.isNullOrBlank()

                    if (BuildConfig.FLAVOR.contains("uat", ignoreCase = true)) {
                        val mainOrderLabel = when (source) {
                            6 -> "X-Ray Order ID"
                            7 -> "MTB Order ID"
                            else -> "Order ID"
                        }
                        val mainOrderLine = when {
                            orderId != null -> "$mainOrderLabel : $orderId"
                            hasOrderBeenPlaced -> "$mainOrderLabel : Pending"
                            else -> null
                        }
                        val rifOrderLine = when {
                            rifOrderId != null -> "RIF Order ID : $rifOrderId"
                            hasRifOrderBeenPlaced -> "RIF Order ID : Pending"
                            else -> null
                        }
                        val orderIdDisplayText = listOfNotNull(mainOrderLine, rifOrderLine).joinToString("\n")
                        if (orderIdDisplayText.isNotEmpty()) {
                            vital.tvOrderID.visibility = View.VISIBLE
                            vital.tvOrderID.text = orderIdDisplayText
                        } else {
                            vital.tvOrderID.visibility = View.GONE
                        }
                    } else {
                        vital.tvOrderID.visibility = View.GONE
                    }

                    // Additive-only order-status pill/captions/MTB summary strip/RIF block —
                    // see bindOrderStatusExtras for the approved-mockup design language.
                    bindOrderStatusExtras(item, source, tbDiag, canActOnReferral, pref, retryingBenIds, clickListener)

                    vital.btnVitalScreen.setOnClickListener {
                        clickListener?.onClickOrderAction(item, config.action, config.type)
                    }
                } else {
                    vital.tvOrderStatus.visibility = View.GONE
                    vital.btnVitalScreen.text = binding.root.context.getString(R.string.vital_screen)
                    val hasVitals = benIdList.contains(item.benId)
                    vital.btnVitalScreen.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                            binding.root.context,
                            if (hasVitals) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                        )
                    )
                    vital.btnVitalScreen.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )
                    vital.btnVitalScreen.setOnClickListener {
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
            // Examine button ? show filled count X/total
            // Registrar: Anthropometry + TB Screening
            // Nurse: Diagnosis hidden, so total stays 4
            // Counselling Officer: Anthropometry + TB Screening + Contact Follow Up are always required (total = 3);
            // Start TPT (TPT registration) and TPT Follow Up become the 4th and 5th required items (total = 5)
            // only when this beneficiary's ClinicalScreeningStatus answer is TPT_ELIGIBLE (see
            // IContactTracingRepository.observeTptEligibleBenIds) ? otherwise FULL_TREATMENT/NO_TREATMENT
            // beneficiaries would incorrectly get stuck at x/5.
            // Others: all 5 forms
            // Legacy, kept for reference:
//            val currentRole = pref?.getLoggedInUser()?.role
//            val isCounsellingOfficer = currentRole.isCounsellingOfficerRole()
//            val isRegistrar = pref?.getLoggedInUser()?.role.isRegistrationOfficerRole()
//            val isNurse = pref?.getLoggedInUser()?.role.isNurseRole()
//            val isCounsellingOfficerForExamine = pref?.getLoggedInUser()?.role.isCounsellingOfficerRole()
//            val (examineFilledCount, examineTotal) = if (isCounsellingOfficerForExamine) {
//                if (showContactTracingForms) {
//                    val requiredItems = if (isTptEligible) {
//                        listOf(hasAnthropometry, hasTbScreening, hasContactFollowUpDone, hasTptFollowUpDone)
//                    } else {
//                        listOf(hasAnthropometry, hasTbScreening, hasContactFollowUpDone)
//                    }
//                    Pair(requiredItems.count { it }, requiredItems.size)
//                } else {
//                    val filled = listOf(
//                        hasAnthropometry,
//                        hasTbScreening
//                    ).count { it }
//                    Pair(filled, 2)
//                }
//            } else if (isRegistrar) {
//                val filled = listOf(
//                    hasAnthropometry,
//                    hasTbScreening
//                ).count { it }
//                Pair(filled, 2)
//            } else if (isNurse || isCounsellingOfficer) {
//                val filled = listOf(
//                    hasAnthropometry,
//                    isMatched,
//                    hasTbScreening,
//                    hasGeneralOpd
//                ).count { it }
//                Pair(filled, 4)
//            } else {
//                val filled = listOf(
//                    hasAnthropometry,
//                    isMatched,
//                    hasTbScreening,
//                    hasGeneralOpd
//                ).count { it }
//                Pair(filled, 4)
//            }

            // Reused below for relevantUnsynced/relevantSyncing too, not just the denominator.
            val assignedRoles = roleManager?.assignedRoles.orEmpty()
            val isRegistrar = AppRole.REGISTRAR in assignedRoles
            val isNurse = AppRole.NURSE in assignedRoles
            val isCounsellingOfficer = AppRole.COUNSELING in assignedRoles
            // examinePrivilegesFor() switches to Counselling's own denominator rule when this is
            // the TPT-module card (showContactTracingForms), regardless of other assigned roles.
            val examineDenominatorRule = roleManager?.examinePrivilegesFor(showContactTracingForms)?.examineDenominatorRule
                ?: ExamineDenominatorRule.GENERIC_FOUR
            val (examineFilledCount, examineTotal) = when (examineDenominatorRule) {
                ExamineDenominatorRule.COUNSELLING_DYNAMIC -> {
                    if (showContactTracingForms) {
                        val requiredItems = if (isTptEligible) {
                            listOf(hasAnthropometry, hasTbScreening, hasContactFollowUpDone, hasTptRegistrationDone, hasTptFollowUpDone)
                        } else {
                            listOf(hasAnthropometry, hasTbScreening, hasContactFollowUpDone)
                        }
                        Pair(requiredItems.count { it }, requiredItems.size)
                    } else {
                        val filled = listOf(
                            hasAnthropometry,
                            hasTbScreening
                        ).count { it }
                        Pair(filled, 2)
                    }
                }
                ExamineDenominatorRule.REGISTRAR_TWO -> {
                    val filled = listOf(
                        hasAnthropometry,
                        hasTbScreening
                    ).count { it }
                    Pair(filled, 2)
                }
                ExamineDenominatorRule.GENERIC_FOUR -> {
                    val filled = listOf(
                        hasAnthropometry,
                        isMatched,
                        hasTbScreening,
                        hasGeneralOpd
                    ).count { it }
                    Pair(filled, 4)
                }
            }

            binding.btnExamine.text = binding.root.context.getString(
                R.string.btn_examine_count_ben,
                examineFilledCount,
                examineTotal
            )


            // Red → none filled, Orange → partially filled, Green → all filled
            val examineTint = when {
                examineFilledCount <= 0 -> android.R.color.holo_red_dark
                examineFilledCount >= examineTotal -> android.R.color.holo_green_dark
                else -> android.R.color.holo_orange_dark
            }
            binding.btnExamine.setBackgroundTintList(
                ContextCompat.getColorStateList(binding.root.context, examineTint)
            )
            binding.btnExamine.setTextColor(
                ContextCompat.getColor(binding.root.context, android.R.color.white)
            )
            binding.btnExamine.visibility = if (showExamineButton) View.VISIBLE else View.GONE

            // Register Wife / Register Husband: Registrar tab only (hidden on the Nurse and
            // Counselling tabs and in TPT/Referral workflows). Add Children is not used. The
            // buttons are a ViewStub, built only for a row that shows one.
            val showRegisterSpouseButtons =
                roleManager?.privilegesForActiveRole()?.showRegisterSpouseButtons == true &&
                !showContactTracingForms &&
                source !in 5..8
            val canAddSpouse = showRegisterSpouseButtons && !item.isNonHH && item.isMarried &&
                !item.isSpouseAdded && !item.isDeath && !item.isDeactivate
            val addSpouseLabel = when {
                canAddSpouse && item.gender == "MALE" -> R.string.add_wife
                canAddSpouse && item.gender == "FEMALE" -> R.string.add_husband
                else -> null
            }
            if (addSpouseLabel == null) {
                addSpouseOrNull?.llAddSpouseBtn?.visibility = View.GONE
            } else {
                addSpouse.llAddSpouseBtn.visibility = View.VISIBLE
                addSpouse.btnAddChildren.visibility = View.GONE
                addSpouse.btnAddSpouse.visibility = View.VISIBLE
                addSpouse.btnAddSpouse.text = context.getString(addSpouseLabel)
                addSpouse.btnAddSpouse.setOnClickListener {
                    if (item.gender == "MALE") clickListener?.onClickedWifeBen(item)
                    else clickListener?.onClickedHusbandBen(item)
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
                        Gender.MALE.name -> if (item.ageInt >= 60) R.drawable.ic_health_old_man else R.drawable.ic_males
                        Gender.FEMALE.name -> if (item.ageInt >= 60) R.drawable.ic_health_old_woman else R.drawable.ic_icon_female_2
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
                    binding.ivSyncState.visibility = View.GONE
                    binding.btnAbha.visibility = View.GONE
                }
                item.isDeactivate -> {
                    binding.contentLayout.setBackgroundColor(ContextCompat.getColor(binding.contentLayout.context, R.color.Quartenary))
                    binding.btnAbha.visibility = View.INVISIBLE
                    binding.tvTitleDuplicaterecord.visibility = View.VISIBLE
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
    private val tptRegistrationDoneIds = mutableListOf<Long>()
    private val tbDiagnosticsList = mutableListOf<TbDiagnosticsCardFields>()

    /**
     * The same rows as [tbDiagnosticsList], keyed by benId. bind() used to locate a row with
     * `tbDiagnosticsList.find { it.benId == item.benId }` - a linear scan of the whole diagnostics
     * table (9,987 entities on a camp device) run twice for every row bound, on the main thread.
     * bind() takes only this map now; the list is kept here purely to diff against the next
     * emission.
     */
    private val tbDiagnosticsByBenId = mutableMapOf<Long, TbDiagnosticsCardFields>()

    /** Set by [submitTBDiagnostics]; until then a missing row means "not loaded yet". */
    private var tbDiagnosticsLoaded = false

    /**
     * The benIds the latest diagnostics snapshot was read for, when the screen reads them per
     * loaded row ([LoadedRowBadges]); null for a whole-table list. A row outside it is "not
     * loaded yet" even after the first snapshot - see [isTbDiagnosticsLoadedFor].
     */
    private var tbDiagnosticsCoveredBenIds: Set<Long>? = null

    private fun isTbDiagnosticsLoadedFor(benId: Long): Boolean =
        tbDiagnosticsLoaded && (tbDiagnosticsCoveredBenIds?.contains(benId) ?: true)

    /**
     * Per-row snapshot from [LoadedRowBadges]: rows whose benId the snapshot just started
     * covering are rebound, so they leave the "Checking" placeholder even when they have no
     * diagnostics row at all.
     */
    fun submitTBDiagnostics(snapshot: TbDiagnosticsSnapshot) {
        val previouslyCovered = tbDiagnosticsCoveredBenIds.orEmpty()
        tbDiagnosticsCoveredBenIds = snapshot.coveredBenIds
        submitTBDiagnostics(snapshot.rows, newlyCovered = snapshot.coveredBenIds - previouslyCovered)
    }

    private val tbScreeningMap = mutableMapOf<Long, TbScreeningSymptoms>()
    private val householdMemberCountMap = mutableMapOf<Long, Int>()
    private val householdHeadNameMap = mutableMapOf<Long, String>()
    var source: Int = 0

    override fun onBindViewHolder(holder: BenViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            item,
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
            tptRegistrationDoneBenIds = tptRegistrationDoneIds,
            showActionButtons = showActionButtons,
            showResultButton = showResultButton,
            showAnthropometryButton = showAnthropometryButton,
            showScreeningStatus = showScreeningStatus,
            tbDiagnosticsByBenId = tbDiagnosticsByBenId,
            tbDiagnosticsLoaded = isTbDiagnosticsLoadedFor(item.benId),
            tbScreeningMap = tbScreeningMap,
            showRedesignedCard = showRedesignedCard,
            householdMemberCountMap = householdMemberCountMap,
            householdHeadNameMap = householdHeadNameMap,
            source = source,
            showExamineButton = showExamineButton,
            showContactTracingForms = showContactTracingForms,
            roleManager = roleManager,
            showAddMemberButton = showAddMemberButton

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

    fun submitTBDiagnostics(list: List<TbDiagnosticsCardFields>, newlyCovered: Set<Long> = emptySet()) {
        // Diff instead of notifyDataSetChanged() — a blanket refresh here rebinds every visible
        // row (including its click listeners) on every emission, which was the root cause of
        // cards needing multiple taps to register.
        val oldByBenId = tbDiagnosticsList.associateBy { it.benId }
        val newByBenId = list.associateBy { it.benId }
        tbDiagnosticsList.clear()
        tbDiagnosticsList.addAll(list)
        tbDiagnosticsByBenId.clear()
        tbDiagnosticsByBenId.putAll(newByBenId)
        // A beneficiary with no diagnostics row at all appears in neither map, so the diff below
        // never names it. Before the first emission such a card renders the "not loaded yet"
        // placeholder, and without a rebind here it would stay on that disabled placeholder for
        // good. Only the first load needs the blanket refresh.
        val firstLoad = !tbDiagnosticsLoaded
        tbDiagnosticsLoaded = true
        if (firstLoad) {
            notifyDataSetChanged()
            return
        }
        val changedBenIds = (oldByBenId.keys + newByBenId.keys)
            .filterTo(mutableSetOf()) { benId -> oldByBenId[benId] != newByBenId[benId] }
        changedBenIds += newlyCovered
        if (changedBenIds.isNotEmpty() || firstLoad) {
            currentList.forEachIndexed { index, item ->
                if (item.benId in changedBenIds || (firstLoad && item.benId !in newByBenId)) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun submitTbScreeningList(list: List<TbScreeningSymptoms>) {
        val old = tbScreeningMap.toMap()
        val new = list.associateBy { it.benId }
        tbScreeningMap.clear()
        tbScreeningMap.putAll(new)
        val changed = (old.keys + new.keys).filterTo(mutableSetOf()) { old[it] != new[it] }
        if (changed.isNotEmpty()) {
            currentList.forEachIndexed { index, item ->
                if (item.benId in changed) notifyItemChanged(index)
            }
        }
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
    fun submitTptRegistrationDoneBenIds(list: List<Long>)  = applyIdList(tptRegistrationDoneIds, list)

    fun submitHouseholdMemberCounts(map: Map<Long, Int>) {
        val old = householdMemberCountMap.toMap()
        householdMemberCountMap.clear()
        householdMemberCountMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            currentList.forEachIndexed { index, item ->
                if (item.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

    fun submitHouseholdHeadNames(map: Map<Long, String>) {
        val old = householdHeadNameMap.toMap()
        householdHeadNameMap.clear()
        householdHeadNameMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            currentList.forEachIndexed { index, item ->
                if (item.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

    companion object {

        // Approximate number of message characters shown before truncation and "See more" is appended.
        const val ERROR_MSG_COLLAPSED_BODY_LENGTH = 30
        fun compactAge(age: String): String {
            val years = Regex("\\d+\\s+Years?", RegexOption.IGNORE_CASE).find(age)
            val months = Regex("\\d+\\s+Months?", RegexOption.IGNORE_CASE).find(age)
            val days = Regex("\\d+\\s+Days?", RegexOption.IGNORE_CASE).find(age)
            return years?.value ?: months?.value ?: days?.value ?: age
        }

        fun normalizedName(name: String): String =
            name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)

        fun Context.formatScreeningResult(value: String?): String {
            val displayValue = value?.trim()?.takeUnless {
                it.isEmpty() || it.equals("N/A", ignoreCase = true)
            }
            return if (displayValue == null) {
                getString(R.string.ben_card_result_title)
            } else {
                getString(R.string.ben_card_result, displayValue)
            }
        }
    }

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
        private val clickedOrderAction: (item: BenBasicDomain, action: OrderAction, orderType: DiagnosticOrderType?) -> Unit = { _, _, _ -> },
        private val clickedGeneralOpd: (item: BenBasicDomain, benId: Long, hhId: Long, viewOnly: Boolean) -> Unit = { _, _, _, _ -> },
        private val clickedAnthropometry: (item: BenBasicDomain, benId: Long, hhId: Long, viewOnly: Boolean) -> Unit = { _, _, _, _ -> },
        private val clickedExamine: (item: BenBasicDomain, benId: Long) -> Unit = { _, _ -> },
        private val clickedNonHHHousehold: (item: BenBasicDomain) -> Unit = {},
        private val clickedAddMember: (item: BenBasicDomain) -> Unit = {}
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
        fun onClickOrderAction(item: BenBasicDomain, action: OrderAction, orderType: DiagnosticOrderType?) =
            clickedOrderAction(item, action, orderType)
        fun onClickGeneralOpd(item: BenBasicDomain, viewOnly: Boolean) =
            clickedGeneralOpd(item, item.benId, item.hhId, viewOnly)
        fun onClickAnthropometry(item: BenBasicDomain, viewOnly: Boolean) =
            clickedAnthropometry(item, item.benId, item.hhId, viewOnly)

        fun onClickedForCall(item: BenBasicDomain) = callBen(item)
        fun onClickSoftDeleteBen(item: BenBasicDomain) = softDeleteBen(item)
        fun onClickExamine(item: BenBasicDomain) = clickedExamine(item, item.benId)
        fun onClickNonHHHousehold(item: BenBasicDomain) = clickedNonHHHousehold(item)
        fun onClickAddMember(item: BenBasicDomain) = clickedAddMember(item)
    }
}
