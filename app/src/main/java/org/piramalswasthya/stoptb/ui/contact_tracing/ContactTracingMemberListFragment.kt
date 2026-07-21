package org.piramalswasthya.stoptb.ui.contact_tracing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.contactTracing.ContactMemberListAdapter
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

@AndroidEntryPoint
class ContactTracingMemberListFragment : Fragment() {

    private val viewModel: ContactTracingMemberListViewModel by viewModels()
    private lateinit var adapter: ContactMemberListAdapter

    private var indexCaseBenId: Long = 0
    private lateinit var contactType: String
    private lateinit var formType: FormType

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_contact_tracing_member_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        indexCaseBenId = arguments?.getLong(ARG_INDEX_CASE_BEN_ID) ?: 0
        contactType = arguments?.getString(ARG_CONTACT_TYPE) ?: "COMMUNITY"
        formType = FormType.valueOf(arguments?.getString(ARG_FORM_TYPE) ?: FormType.CONTACT_TRACING_COMMUNITY.name)

        view.findViewById<android.widget.TextView>(R.id.tv_member_list_header).text =
            if (contactType == "OCCUPATIONAL") getString(R.string.occupational_contact_tracing)
            else getString(R.string.community_contact_tracing)

        val rv = view.findViewById<RecyclerView>(R.id.rv_members)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = ContactMemberListAdapter { member ->
            (requireActivity() as? ContactTracingNavigator)?.resumeContactForm(formType, member.responseId)
        }
        rv.adapter = adapter

        val emptyState = view.findViewById<View>(R.id.tv_empty_state)
        viewModel.members.observe(viewLifecycleOwner) { members ->
            adapter.submitList(members)
            emptyState.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
            rv.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        }

        view.findViewById<ExtendedFloatingActionButton>(R.id.fab_add_member).setOnClickListener {
            (requireActivity() as? ContactTracingNavigator)?.openNewContactForm(formType, contactType, null)
        }

        viewModel.load(indexCaseBenId, contactType)
    }

    companion object {
        private const val ARG_INDEX_CASE_BEN_ID = "indexCaseBenId"
        private const val ARG_CONTACT_TYPE = "contactType"
        private const val ARG_FORM_TYPE = "formType"

        fun newInstance(indexCaseBenId: Long, contactType: String, formType: FormType) = ContactTracingMemberListFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_INDEX_CASE_BEN_ID, indexCaseBenId)
                putString(ARG_CONTACT_TYPE, contactType)
                putString(ARG_FORM_TYPE, formType.name)
            }
        }
    }
}
