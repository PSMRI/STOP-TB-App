package org.piramalswasthya.stoptb.ui.counselling_activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.dynamicAdapter.CounsellingDynamicAdapter

@AndroidEntryPoint
class CounsellingFormFragment : Fragment() {

    private val viewModel: CounsellingViewModel by activityViewModels()
    private lateinit var adapter: CounsellingDynamicAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_counselling_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_counselling_form)
        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = CounsellingDynamicAdapter(emptyList()) { updatedQ ->
            viewModel.evaluateConditions(updatedQ)
        }
        rv.adapter = adapter

        viewModel.activeQuestions.observe(viewLifecycleOwner) { questions ->
            adapter.submitList(questions)
        }

        val tvLetter = view.findViewById<android.widget.TextView>(R.id.tv_section_letter)
        val tvName = view.findViewById<android.widget.TextView>(R.id.tv_section_name)

        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            val section = viewModel.schemaData?.sections?.getOrNull(step)
            section?.let {
                // Determine letter from sectionCode, e.g. "SECTION_A" -> "A"
                // Using safe calls because Gson might leave non-nullable fields as null if missing in JSON
                val letter = if (it.sectionPhase == "POST_SUBMIT") "F"
                else ('A' + it.displayOrder - 1).toChar().toString()
                tvLetter.text = letter
                tvName.text = it.sectionName
            }
        }
    }
}
