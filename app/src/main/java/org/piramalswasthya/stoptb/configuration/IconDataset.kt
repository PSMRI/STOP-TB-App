package org.piramalswasthya.stoptb.configuration
import android.content.res.Resources
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.model.Icon
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.ui.home_activity.communicable_diseases.CdFragmentDirections
import org.piramalswasthya.stoptb.ui.home_activity.home.ReferralIconsFragmentDirections
import org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.NcdFragmentDirections
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
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
    fun getVolunteerIconDataset(resources: Resources): List<Icon> {
        val role = preferenceDao.getLoggedInUser()?.role
        val iconList = mutableListOf(
            Icon(
                R.drawable.ic__hh,
                resources.getString(R.string.icon_title_household),
                resources.getString(R.string.home_card_household_subtitle),
                recordsRepo.hhListCount,
                VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToAllHouseholdFragment()
            ),
            Icon(
                R.drawable.ic__ben,
                resources.getString(R.string.icon_title_ben),
                resources.getString(R.string.home_card_all_ben_subtitle),
                recordsRepo.allBenListCount,
                VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToAllBenFragment()
            )
        )

        if (role.isRegistrationOfficerRole() || role.isCounsellingOfficerRole() || role.isNurseRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic__ben,
                    resources.getString(R.string.icon_title_non_hh),
                    resources.getString(R.string.home_card_non_hh_subtitle),
                    recordsRepo.nonHHListCount,
                    VolunteerHomeFragmentDirections
                        .actionVolunteerHomeFragmentToNonHHFragment()
                )
            )
        }

        if (role.isNurseRole() || role.isCounsellingOfficerRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic__ncd,
                    resources.getString(R.string.tuberculosis),
                    resources.getString(R.string.home_card_tb_subtitle),
                    null,
                    VolunteerHomeFragmentDirections
                        .actionVolunteerHomeFragmentToTbFragment()
                )
            )

            iconList.add(
                Icon(
                    R.drawable.ic_ncd_noneligible,
                    resources.getString(R.string.ncd_refer_list),
                    resources.getString(R.string.home_card_referral_subtitle),
                    null,
                    VolunteerHomeFragmentDirections
                        .actionVolunteerHomeFragmentToReferralIconsFragment()
                )
            )
        }
        /*if (role.isCounsellingOfficerRole()) {
            iconList.removeAll { icon ->
                icon.title != resources.getString(R.string.tuberculosis)
            }
        }*/

        if (role.isCounsellingOfficerRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic_counselling_module,
                    resources.getString(R.string.icon_title_counselling_module),
                    resources.getString(R.string.home_card_counselling_module_subtitle),
                    null,
                    VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToTBConfirmedListFragment(
                        restrictToAction = "COUNSELLING"
                    )
                )
            )
        }
        if (role.isCounsellingOfficerRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic_contact_tracing_module,
                    resources.getString(R.string.icon_title_contact_tracing_module),
                    resources.getString(R.string.home_card_contact_tracing_module_subtitle),
                    null,
                    VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToTBConfirmedListFragment(
                        restrictToAction = "CONTACT_TRACING"
                    )
                )
            )
        }
        if (role.isCounsellingOfficerRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic_tb_treatment_follow_up_module,
                    resources.getString(R.string.icon_title_tb_followup_module),
                    resources.getString(R.string.home_card_tb_followup_module_subtitle),
                    null,
                    VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToTBConfirmedListFragment(
                        restrictToAction = "FOLLOW_UP"
                    )
                )
            )
        }
        if (role.isCounsellingOfficerRole()) {
            iconList.add(
                Icon(
                    R.drawable.ic_tpt_module,
                    resources.getString(R.string.icon_title_tpt_module),
                    resources.getString(R.string.home_card_tpt_module_subtitle),
                    null,
                    VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToAllBenFragment(
                        showContactTracingForms = true
                    )
                )
            )
        }

        return iconList.apply {
            forEachIndexed { index, icon ->
                icon.colorPrimary = index % 2 == 0
            }
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
    fun getCDDataset(resources: Resources): List<Icon> {
        val iconList = mutableListOf(
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
                navAction = CdFragmentDirections.actionCdFragmentToTBConfirmedListFragment(
                    restrictToAction = "VIEW_ONLY"
                )
            )
        )
        /*if (role.isCounsellingOfficerRole()) {
            iconList.removeAll { icon ->
                icon.title != resources.getString(R.string.icon_title_ncd_tb_confirmed)
            }
        }*/
        return iconList.apply {
            forEachIndexed { index, icon ->
                icon.colorPrimary = index % 2 == 0
            }
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
