package org.piramalswasthya.stoptb.ui.contact_tracing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

@AndroidEntryPoint
class ContactTracingActivity : AppCompatActivity(), ContactTracingNavigator {

    private var indexCaseBenId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_tracing)

        indexCaseBenId = intent.getLongExtra(EXTRA_BEN_ID, 0)
        val directContactBenId = intent.getLongExtra(EXTRA_DIRECT_HOUSEHOLD_CONTACT_BEN_ID, -1L)
            .takeIf { it > 0 }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_contact_tracing)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onSupportNavigateUp() }

        if (savedInstanceState == null) {
            if (directContactBenId != null) {
                // Reached via the "Trace Contact" button on a household member row —
                // skip the Selector and open the HHC form for that member directly.
                title = getString(R.string.household_contact_tracing)
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        ContactTracingFormFragment.newInstance(
                            FormType.CONTACT_TRACING_HOUSEHOLD, indexCaseBenId, directContactBenId, "HOUSEHOLD"
                        )
                    )
                    .commitNow()
            } else {
                title = getString(R.string.contact_tracing_selector_title)
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        ContactTracingFormFragment.newInstance(
                            FormType.CONTACT_TRACING_SELECTOR, indexCaseBenId, null, "SELECTOR"
                        )
                    )
                    .commitNow()
            }
        }
    }

    override fun openMemberList(contactType: String) {
        val formType = if (contactType == "OCCUPATIONAL") FormType.CONTACT_TRACING_OCCUPATIONAL else FormType.CONTACT_TRACING_COMMUNITY
        title = if (contactType == "OCCUPATIONAL") getString(R.string.occupational_contact_tracing) else getString(R.string.community_contact_tracing)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ContactTracingMemberListFragment.newInstance(indexCaseBenId, contactType, formType))
            .addToBackStack(null)
            .commit()
    }

    override fun openNewContactForm(formType: FormType, contactType: String, contactBenId: Long?) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ContactTracingFormFragment.newInstance(formType, indexCaseBenId, contactBenId, contactType))
            .addToBackStack(null)
            .commit()
    }

    override fun resumeContactForm(formType: FormType, responseId: Long) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ContactTracingFormFragment.resumeInstance(formType, responseId))
            .addToBackStack(null)
            .commit()
    }

    override fun showHouseholdRoutingNote() {
        Toast.makeText(this, R.string.contact_tracing_household_note, Toast.LENGTH_LONG).show()
    }

    override fun onFormCompleted() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            Toast.makeText(this, R.string.btn_submit, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
        return true
    }

    companion object {
        const val EXTRA_BEN_ID = "contact_tracing_index_case_ben_id"
        const val EXTRA_DIRECT_HOUSEHOLD_CONTACT_BEN_ID = "contact_tracing_direct_household_contact_ben_id"

        fun start(context: Context, indexCaseBenId: Long) {
            context.startActivity(
                Intent(context, ContactTracingActivity::class.java).putExtra(EXTRA_BEN_ID, indexCaseBenId)
            )
        }

        /** Launched from the "Trace Contact" button on a household member row — skips
         * the Selector and opens the HHC form for that specific member directly. */
        fun startForHouseholdContact(context: Context, indexCaseBenId: Long, contactBenId: Long) {
            context.startActivity(
                Intent(context, ContactTracingActivity::class.java)
                    .putExtra(EXTRA_BEN_ID, indexCaseBenId)
                    .putExtra(EXTRA_DIRECT_HOUSEHOLD_CONTACT_BEN_ID, contactBenId)
            )
        }
    }
}
