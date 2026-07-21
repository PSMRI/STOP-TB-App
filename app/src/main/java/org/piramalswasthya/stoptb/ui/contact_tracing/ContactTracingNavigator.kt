package org.piramalswasthya.stoptb.ui.contact_tracing

import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

/** Implemented by ContactTracingActivity; Fragments call up through this rather than
 * casting to the concrete Activity class directly. */
interface ContactTracingNavigator {
    fun openMemberList(contactType: String)
    fun openNewContactForm(formType: FormType, contactType: String, contactBenId: Long?)
    fun resumeContactForm(formType: FormType, responseId: Long)
    fun showHouseholdRoutingNote()
    fun onFormCompleted()
}
