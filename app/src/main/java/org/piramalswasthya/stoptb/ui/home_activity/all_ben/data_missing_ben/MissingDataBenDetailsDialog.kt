package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.DialogMissingDataBenDetailsBinding

object MissingDataBenDetailsDialog {

    fun show(
        context: Context,
        item: MissingDataBenItem,
        onEditClicked: (MissingDataBenItem) -> Unit
    ) {
        val layoutInflater = LayoutInflater.from(context)
        val dialogBinding = DialogMissingDataBenDetailsBinding.inflate(layoutInflater)

        dialogBinding.tvDialogName.text = item.benBasicDomain.benName
        dialogBinding.tvDialogBeneficiaryId.text = context.getString(R.string.beneficiary_id) + ": ${item.id}"
        dialogBinding.tvDialogReasons.text = if (item.reasons.isNotEmpty()) {
            item.reasons.joinToString("\n") { "• $it" }
        } else {
            context.getString(R.string.no_reasons_specified)
        }

        val alertDialog = MaterialAlertDialogBuilder(
            context,
            R.style.ThemeOverlay_App_Dialog
        )
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnEdit.setOnClickListener {
            alertDialog.dismiss()
            onEditClicked(item)
        }

        alertDialog.show()
    }
}
