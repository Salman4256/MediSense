package com.medisense.app.ui.healthrecord.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.medisense.app.databinding.FragmentHealthRecordBinding
import com.medisense.app.ui.healthrecord.viewmodel.HealthRecordUiState
import com.medisense.app.ui.healthrecord.viewmodel.HealthRecordViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@AndroidEntryPoint
class HealthRecordFragment : Fragment() {

    private var _binding: FragmentHealthRecordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthRecordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupDropdowns()
        setupDatePicker()
        setupListeners()
        observeUiState()

        viewModel.loadProfile()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private val countryCodes = arrayOf(
        "+91 (IN)",
        "+1 (US/CA)",
        "+44 (UK)",
        "+61 (AU)",
        "+971 (AE)",
        "+65 (SG)",
        "+60 (MY)",
        "+49 (DE)",
        "+33 (FR)",
        "+81 (JP)",
        "+86 (CN)",
        "+966 (SA)",
        "+92 (PK)",
        "+880 (BD)",
        "+94 (LK)",
        "+977 (NP)",
        "+234 (NG)",
        "+27 (ZA)",
        "+55 (BR)",
        "+62 (ID)",
        "+63 (PH)",
        "+7 (RU)",
        "+39 (IT)",
        "+34 (ES)",
        "+82 (KR)"
    )

    private fun setupDropdowns() {
        val genders = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genders)
        binding.actvGender.setAdapter(genderAdapter)

        val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        val bloodGroupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bloodGroups)
        binding.actvBloodGroup.setAdapter(bloodGroupAdapter)

        val countryCodeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, countryCodes)
        binding.actvCountryCode.setAdapter(countryCodeAdapter)
        binding.actvCountryCode.setText("+91 (IN)", false)
    }

    private fun setupDatePicker() {
        binding.etDob.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val date = Instant.ofEpochMilli(selection)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                binding.etDob.setText(date.toString())
            }

            datePicker.show(parentFragmentManager, "DOB_PICKER")
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val dob = binding.etDob.text.toString().trim()
            val gender = binding.actvGender.text.toString().trim()
            val bloodGroup = binding.actvBloodGroup.text.toString().trim()
            val height = binding.etHeight.text.toString().trim()
            val weight = binding.etWeight.text.toString().trim()
            val allergies = binding.etAllergies.text.toString().trim()
            val diseases = binding.etDiseases.text.toString().trim()
            val medications = binding.etMedications.text.toString().trim()
            val familyHistory = binding.etFamilyHistory.text.toString().trim()
            val emergencyName = binding.etEmergencyName.text.toString().trim()
            val rawPhone = binding.etEmergencyPhone.text.toString().trim()
            val selectedCodeText = binding.actvCountryCode.text.toString().trim()
            val codePrefix = selectedCodeText.substringBefore(" ").trim()
            val emergencyPhone = if (rawPhone.isNotBlank()) {
                if (rawPhone.startsWith("+")) rawPhone else "$codePrefix $rawPhone"
            } else ""
            val notes = binding.etNotes.text.toString().trim()

            viewModel.saveOrUpdateProfile(
                fullName = fullName,
                dob = dob,
                gender = gender,
                bloodGroup = bloodGroup,
                heightStr = height,
                weightStr = weight,
                allergies = allergies,
                diseases = diseases,
                medications = medications,
                familyHistory = familyHistory,
                emergencyName = emergencyName,
                emergencyPhone = emergencyPhone,
                notes = notes
            )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HealthRecordUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnSave.isEnabled = false
                        }
                        is HealthRecordUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                        }
                        is HealthRecordUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                        is HealthRecordUiState.ProfileLoaded -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                            val profile = state.profile
                            binding.etFullName.setText(profile.fullName)
                            binding.etDob.setText(profile.dateOfBirth)
                            binding.actvGender.setText(profile.gender, false)
                            binding.actvBloodGroup.setText(profile.bloodGroup, false)
                            binding.etHeight.setText(profile.height?.toString() ?: "")
                            binding.etWeight.setText(profile.weight?.toString() ?: "")
                            binding.etAllergies.setText(profile.allergies)
                            binding.etDiseases.setText(profile.existingDiseases)
                            binding.etMedications.setText(profile.currentMedications)
                            binding.etFamilyHistory.setText(profile.familyHistory)
                            binding.etEmergencyName.setText(profile.emergencyContactName)
                            
                            val rawSavedPhone = profile.emergencyContactNumber ?: ""
                            if (rawSavedPhone.isNotBlank()) {
                                val matchedCode = countryCodes.firstOrNull { rawSavedPhone.startsWith(it.substringBefore(" ")) }
                                if (matchedCode != null) {
                                    val prefix = matchedCode.substringBefore(" ")
                                    val remainingPhone = rawSavedPhone.removePrefix(prefix).trim()
                                    binding.actvCountryCode.setText(matchedCode, false)
                                    binding.etEmergencyPhone.setText(remainingPhone)
                                } else {
                                    binding.actvCountryCode.setText("+91 (IN)", false)
                                    binding.etEmergencyPhone.setText(rawSavedPhone)
                                }
                            } else {
                                binding.actvCountryCode.setText("+91 (IN)", false)
                                binding.etEmergencyPhone.setText("")
                            }
                            
                            binding.etNotes.setText(profile.notes)
                            binding.btnSave.text = "Update Health Record"
                        }
                        is HealthRecordUiState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
