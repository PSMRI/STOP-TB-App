package org.piramalswasthya.stoptb.ui.home_activity.non_hh

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentCurrentLivingInfoBinding

@AndroidEntryPoint
class CurrentLivingInfoFragment : Fragment() {

    private var _binding: FragmentCurrentLivingInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrentLivingInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val choices = resources.getStringArray(R.array.place_of_current_living_choices)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, choices)
        binding.actPlaceOfLiving.setAdapter(adapter)

        binding.actPlaceOfLiving.setOnItemClickListener { _, _, position, _ ->
            binding.tilPlaceOfLiving.error = null
            handleVisibility(position)
        }

        binding.btnContinue.setOnClickListener {
            if (validateForm()) {
                val position = choices.indexOf(binding.actPlaceOfLiving.text.toString())
                val placeIndex = position + 1
                val otherPlace = if (position == 16) binding.etOtherPlace.text.toString().trim() else null
                val instName = if (position in listOf(8, 10, 11, 12, 13, 14, 15)) {
                    binding.etInstitutionName.text.toString().trim()
                } else null

                findNavController().navigate(
                    CurrentLivingInfoFragmentDirections.actionCurrentLivingInfoFragmentToNewBenRegFragment(
                        isNonHH = true,
                        placeOfCurrentLiving = placeIndex,
                        otherPlace = otherPlace,
                        institutionName = instName,
                        hhId = 0L,
                        relToHeadId = 18,
                        gender = 0,
                        benId = 0L,
                        selectedBenId = 0L,
                        isAddSpouse = 0
                    )
                )
            }
        }
    }

    private fun handleVisibility(position: Int) {
        // Option index 16 is "Other"
        if (position == 16) {
            binding.tilOtherPlace.visibility = View.VISIBLE
        } else {
            binding.tilOtherPlace.visibility = View.GONE
            binding.etOtherPlace.setText("")
            binding.tilOtherPlace.error = null
        }

        // Option index 8 (Educational Inst), 10 (Rehab), 11 (Orphanage), 12 (Old Age), 13 (Private Hostel), 14 (Govt Hostel), 15 (NGO Hostel)
        if (position in listOf(8, 10, 11, 12, 13, 14, 15)) {
            binding.tilInstitutionName.visibility = View.VISIBLE
        } else {
            binding.tilInstitutionName.visibility = View.GONE
            binding.etInstitutionName.setText("")
            binding.tilInstitutionName.error = null
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val selection = binding.actPlaceOfLiving.text.toString()
        if (selection.isEmpty()) {
            binding.tilPlaceOfLiving.error = "Place of Current Living is required"
            isValid = false
        } else {
            binding.tilPlaceOfLiving.error = null
        }

        val choices = resources.getStringArray(R.array.place_of_current_living_choices)
        val position = choices.indexOf(selection)

        if (position == 16) {
            val otherPlaceText = binding.etOtherPlace.text.toString().trim()
            if (otherPlaceText.isEmpty()) {
                binding.tilOtherPlace.error = "Other Place is required"
                isValid = false
            } else {
                binding.tilOtherPlace.error = null
            }
        }

        if (position in listOf(8, 10, 11, 12, 13, 14, 15)) {
            val instName = binding.etInstitutionName.text.toString().trim()
            if (instName.isEmpty()) {
                binding.tilInstitutionName.error = "Name of Institution is required"
                isValid = false
            } else {
                binding.tilInstitutionName.error = null
            }
        }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
