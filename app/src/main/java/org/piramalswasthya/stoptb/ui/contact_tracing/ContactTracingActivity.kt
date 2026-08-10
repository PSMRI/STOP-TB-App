package org.piramalswasthya.stoptb.ui.contact_tracing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.ActivityContactTracingBinding
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

@AndroidEntryPoint
class ContactTracingActivity : AppCompatActivity(), ContactTracingNavigator {

    private lateinit var binding: ActivityContactTracingBinding

    private val indexCaseBenId: Long by lazy {
        intent.getLongExtra(EXTRA_BEN_ID, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContactTracingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBackPressHandling()
        supportFragmentManager.addOnBackStackChangedListener {
            updateTitleForCurrentFragment()
        }

        val contactType = intent.getStringExtra(EXTRA_CONTACT_TYPE) ?: CONTACT_TYPE_COMMUNITY
        val formType = when (contactType) {
            CONTACT_TYPE_OCCUPATIONAL -> FormType.OCCUPATION_CONTACT_TRACING
            CONTACT_TYPE_FOLLOW_UP -> FormType.CONTACT_FOLLOW_UP
            CONTACT_TYPE_TPT_FOLLOW_UP -> FormType.TPT_FOLLOW_UP
            else -> FormType.COMMUNITY_CONTACT_TRACING
        }
        val viewHistory = intent.getBooleanExtra(EXTRA_VIEW_HISTORY, false)

        // Restore screen title on activity recreation (e.g. orientation change) before early return
        updateTitle(viewHistory,formType)
        if (savedInstanceState != null) return

        val sectionPhase = intent.getStringExtra(EXTRA_SECTION_PHASE)
            ?.let { runCatching { SectionPhase.valueOf(it) }.getOrNull() }
        openContactForm(formType, contactType, sectionPhase, viewHistory = viewHistory)
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (current is ContactTracingFormFragment) {
                    // Save whatever's filled before actually navigating away — see Bug 4.
                    current.saveDraftAndGoBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarContactTracing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbarContactTracing.setNavigationOnClickListener {
            onSupportNavigateUp()
        }
    }

    private fun updateTitleForCurrentFragment() {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
                as? ContactTracingFormFragment ?: return
        updateTitle(current.screenViewHistory, current.screenFormType)
    }

    private fun updateTitle(viewHistory: Boolean,formType : FormType){
        title = if (viewHistory) {
            getString(R.string.follow_up_history)
        } else when (formType) {
            FormType.OCCUPATION_CONTACT_TRACING -> getString(R.string.occupational_contact_tracing)
            FormType.CONTACT_FOLLOW_UP -> getString(R.string.contact_tracing_follow_up)
            FormType.TPT_FOLLOW_UP -> getString(R.string.tpt_follow_up)
            else -> getString(R.string.community_contact_tracing)
        }
    }

    override fun openContactForm(
        formType: FormType,
        contactType: String,
        sectionPhase: SectionPhase?,
        addToBackStack: Boolean,
        viewHistory: Boolean
    ) {
        updateTitle(viewHistory,formType)
        val transaction = supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                ContactTracingFormFragment.newInstance(formType, indexCaseBenId, contactType, sectionPhase, viewHistory)
            )
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    override fun onFormCompleted() {
        finish()
    }

    override fun onBackNavigation() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_BEN_ID = "contact_tracing_index_case_ben_id"
        const val EXTRA_CONTACT_TYPE = "contact_tracing_contact_type"
        const val EXTRA_SECTION_PHASE = "contact_tracing_section_phase"
        const val EXTRA_VIEW_HISTORY = "contact_tracing_view_history"

        const val CONTACT_TYPE_COMMUNITY = "COMMUNITY"
        const val CONTACT_TYPE_OCCUPATIONAL = "OCCUPATIONAL"
        const val CONTACT_TYPE_FOLLOW_UP = "CONTACT_FOLLOW_UP"
        const val CONTACT_TYPE_TPT_FOLLOW_UP = "TPT_FOLLOW_UP"

        fun startForType(
            context: Context,
            indexCaseBenId: Long,
            contactType: String,
            sectionPhase: SectionPhase? = null,
            viewHistory: Boolean = false
        ) {
            context.startActivity(
                Intent(context, ContactTracingActivity::class.java).apply {
                    putExtra(EXTRA_BEN_ID, indexCaseBenId)
                    putExtra(EXTRA_CONTACT_TYPE, contactType)
                    sectionPhase?.let { putExtra(EXTRA_SECTION_PHASE, it.name) }
                    putExtra(EXTRA_VIEW_HISTORY, viewHistory)
                }
            )
        }
    }
}