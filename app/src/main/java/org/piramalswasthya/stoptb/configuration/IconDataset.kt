package org.piramalswasthya.stoptb.configuration

import android.content.res.Resources
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.Icon
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.ui.home_activity.communicable_diseases.CdFragmentDirections
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
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToAllBenFragment()
        ),
        Icon(
            R.drawable.ic__ncd,
            resources.getString(R.string.icon_title_ncd_tb_screening),
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToTbFragment()
        ),
        Icon(
            R.drawable.ic__ncd,
            resources.getString(R.string.icon_title_ncd),
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToNcdFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.ncd_refer_list),
            null,
            VolunteerHomeFragmentDirections.actionVolunteerHomeFragmentToNcdReferredListFragment()
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
            recordsRepo.getNcdEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdEligibleListFragment()
        ),
        Icon(
            R.drawable.ic__ncd_priority,
            resources.getString(R.string.icon_title_ncd_priority_list),
            recordsRepo.getNcdPriorityListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdPriorityListFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.icon_title_ncd_non_eligible_list),
            recordsRepo.getNcdNonEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdNonEligibleListFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.ncd_refer_list),
            recordsRepo.getNcdrefferedListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdReferredListFragment()
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }

    fun getNCDDatasetForVolunteer(resources: Resources) = listOf(
        Icon(
            R.drawable.ic__ncd_eligibility,
            resources.getString(R.string.icon_title_ncd_eligible_list),
            recordsRepo.getNcdEligibleListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdEligibleListFragment()
        ),
        Icon(
            R.drawable.ic__ncd_priority,
            resources.getString(R.string.icon_title_ncd_priority_list),
            recordsRepo.getNcdPriorityListCount,
            NcdFragmentDirections.actionNcdFragmentToNcdPriorityListFragment()
        ),
        Icon(
            R.drawable.ic_ncd_noneligible,
            resources.getString(R.string.icon_title_ncd_non_eligible_list),
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
            recordsRepo.tbScreeningListCount,
            CdFragmentDirections.actionCdFragmentToTBScreeningListFragment()
        ),
        Icon(
            R.drawable.ic__death,
            resources.getString(R.string.icon_title_ncd_tb_suspected),
            recordsRepo.tbSuspectedListCount,
            CdFragmentDirections.actionCdFragmentToTBSuspectedListFragment()
        ),
        Icon(
            icon = R.drawable.ic__death,
            title = resources.getString(R.string.icon_title_ncd_tb_confirmed),
            count = recordsRepo.tbConfirmedListCount,
            navAction = CdFragmentDirections.actionCdFragmentToTBConfirmedListFragment()
        )
    ).apply {
        forEachIndexed { index, icon ->
            icon.colorPrimary = index % 2 == 0
        }
    }
}
