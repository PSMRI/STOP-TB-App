package org.piramalswasthya.stoptb.ui.contact_tracing

import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

/** Implemented by ContactTracingActivity; Fragments call up through this rather than
 * casting to the concrete Activity class directly. */
interface ContactTracingNavigator {

    fun openContactForm(
        formType: FormType,
        contactType: String,
        sectionPhase: SectionPhase? = null,
        addToBackStack: Boolean = false,
        viewHistory: Boolean = false
    )
    fun onFormCompleted()

    fun onBackNavigation()
}
