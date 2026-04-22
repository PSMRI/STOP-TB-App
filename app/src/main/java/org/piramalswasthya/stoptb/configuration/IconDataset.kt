package org.piramalswasthya.stoptb.configuration

import android.content.res.Resources
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.Icon
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.ui.home_activity.communicable_diseases.CdFragmentDirections
import org.piramalswasthya.stoptb.ui.home_activity.home.ReferralIconsFragmentDirections
import org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.NcdFragmentDirections
import org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerHomeFragmentDirections
import javax.inject.Inject

@ActivityRetainedScoped
class IconDataset @Inject constructor(
    private val recordsRepo: RecordsRepo,
    private val preferenceDao: PreferenceDao
) {

    enum class Disease {
        MALARIA, KALA_AZAR, AES_JE, FILARIA, LEPROSY, DEWARMING
    }

    fun getVolunteerIconDataset(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ben,
            resources.getString(R.string.icon_title_ben),
            resources.getString(R.string.home_card_all_ben_subtitle),
            recordsRepo.allBenListCount,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToAllBenFragment()
        ),
        Icon(
            R.drawable.ic__ncd,
            resources.getString(R.string.icon_title_ncd_tb_screening),
            resources.getString(R.string.home_card_tb_subtitle),
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToTbFragment()
        ),
//        Icon(
//            R.drawable.ic__ncd,
//            resources.getString(R.string.icon_title_ncd),
//            resources.getString(R.string.home_card_ncd_subtitle),
//            null,
//            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToNcdFragment()
//        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.ncd_refer_list),
            resources.getString(R.string.home_card_referral_subtitle),
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToReferralIconsFragment()
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }

    fun getNCDDataset(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ncd_eligibility,
            resources.getString(R.string.icon_title_ncd_eligible_list),
            resources.getString(R.string.home_card_ncd_eligible_subtitle),
            recordsRepo.getNcdEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdEligibleListFragment()
        ),
        Icon(
            R.drawable.ic__ncd_priority,
            resources.getString(R.string.icon_title_ncd_priority_list),
            resources.getString(R.string.home_card_ncd_priority_subtitle),
            recordsRepo.getNcdPriorityListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdPriorityListFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.icon_title_ncd_non_eligible_list),
            resources.getString(R.string.home_card_ncd_non_priority_subtitle),
            recordsRepo.getNcdNonEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdNonEligibleListFragment()
        ),
//        Icon(
//            R.drawable.ic_ncd_noneligible,
//            resources.getString(R.string.ncd_refer_list),
//            recordsRepo.getNcdrefferedListCount,
//            NcdFragmentDirections.actionNcdFragmentToNcdReferredListFragment()
//        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }

    fun getNCDDatasetForVolunteer(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ncd_eligibility,
            resources.getString(R.string.icon_title_ncd_eligible_list),
            resources.getString(R.string.home_card_ncd_eligible_subtitle),
            recordsRepo.getNcdEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdEligibleListFragment()
        ),
        Icon(
            R.drawable.ic__ncd_priority,
            resources.getString(R.string.icon_title_ncd_priority_list),
            resources.getString(R.string.home_card_ncd_priority_subtitle),
            recordsRepo.getNcdPriorityListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdPriorityListFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.icon_title_ncd_non_eligible_list),
            resources.getString(R.string.home_card_ncd_non_priority_subtitle),
            recordsRepo.getNcdNonEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdNonEligibleListFragment()
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }

    fun getCDDataset(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ncd_eligibility,
            resources.getString(R.string.icon_title_ncd_tb_screening),
            resources.getString(R.string.home_card_tb_screening_subtitle),
            recordsRepo.tbScreeningListCount,
            CdFragmentDirections.actionCdFragmentToTBScreeningListFragment()
        ),
        Icon(
            R.drawable.ic__death,
            resources.getString(R.string.icon_title_ncd_tb_suspected),
            resources.getString(R.string.home_card_tb_suspected_short_subtitle),
            recordsRepo.tbSuspectedListCount,
            CdFragmentDirections.actionCdFragmentToTBSuspectedListFragment()
        ),
        Icon(
            icon = R.drawable.ic__death,
            title = resources.getString(R.string.icon_title_ncd_tb_confirmed),
            subtitle = resources.getString(R.string.home_card_tb_confirmed_short_subtitle),
            count = recordsRepo.tbConfirmedListCount,
            navAction = CdFragmentDirections.actionCdFragmentToTBConfirmedListFragment()
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }

    fun getReferralDataset(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ncd_eligibility,
            resources.getString(R.string.referral_digital_chest_xray),
            resources.getString(R.string.home_card_referral_subtitle),
            recordsRepo.digitalChestXrayReferralCount,
            ReferralIconsFragmentDirections.actionReferralIconsFragmentToAllBenFragment(6)
        ),
        Icon(
            R.drawable.ic__death,
            resources.getString(R.string.referral_true_nat),
            resources.getString(R.string.home_card_referral_subtitle),
            recordsRepo.trueNatReferralCount,
            ReferralIconsFragmentDirections.actionReferralIconsFragmentToAllBenFragment(7)
        ),
        Icon(
            R.drawable.ic_check_circle,
            resources.getString(R.string.referral_hwc),
            resources.getString(R.string.home_card_referral_subtitle),
            recordsRepo.hwcReferralCount,
            ReferralIconsFragmentDirections.actionReferralIconsFragmentToAllBenFragment(5)
        ),
        Icon(
            R.drawable.ic_check_circle,
            resources.getString(R.string.referral_liquid_culture),
            resources.getString(R.string.home_card_referral_subtitle),
            recordsRepo.liquidCultureReferralCount,
            ReferralIconsFragmentDirections.actionReferralIconsFragmentToAllBenFragment(8)
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }
}
