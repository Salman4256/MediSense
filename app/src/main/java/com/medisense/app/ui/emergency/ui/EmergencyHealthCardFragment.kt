package com.medisense.app.ui.emergency.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentEmergencyHealthCardBinding
import com.medisense.app.domain.model.EmergencyHealthCard
import com.medisense.app.ui.emergency.viewmodel.EmergencyHealthCardUiState
import com.medisense.app.ui.emergency.viewmodel.EmergencyHealthCardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Module 20: Emergency & Critical Health Access Card Fragment.
 *
 * Provides instant, offline-first access to essential health details:
 * Emergency Contact, Known Allergies, Blood Group, Active Medications,
 * Medical Conditions, and Personal Baseline.
 */
@AndroidEntryPoint
class EmergencyHealthCardFragment : Fragment() {

    private var _binding: FragmentEmergencyHealthCardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmergencyHealthCardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyHealthCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupActionButtons()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupActionButtons() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadEmergencyCard()
        }

        binding.btnShareEmergencyCard.setOnClickListener {
            showPrivacyShareDialog()
        }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: EmergencyHealthCardUiState) {
        when (state) {
            is EmergencyHealthCardUiState.Loading -> {
                binding.layoutLoading.isVisible = true
                binding.layoutError.isVisible = false
                binding.scrollViewEmergencyCard.isVisible = false
            }
            is EmergencyHealthCardUiState.Error -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = true
                binding.scrollViewEmergencyCard.isVisible = false
                binding.tvErrorMessage.text = state.message
            }
            is EmergencyHealthCardUiState.Content -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
                binding.scrollViewEmergencyCard.isVisible = true
                bindCardData(state.card)
            }
        }
    }

    private fun bindCardData(card: EmergencyHealthCard) {
        // 1. Completeness Banner
        binding.tvCompletenessBadge.text = "${card.completeness.scorePercentage}% Ready"
        if (card.completeness.missingFields.isNotEmpty()) {
            binding.tvCompletenessDescription.text = "Missing: ${card.completeness.missingFields.joinToString(", ")}. Update your profile to complete."
        } else {
            binding.tvCompletenessDescription.text = "All critical emergency fields are populated and available offline."
        }

        // 2. Emergency Contact
        val hasContactName = card.emergencyContact.name.isNotBlank()
        val hasContactPhone = card.emergencyContact.phone.isNotBlank()

        if (hasContactName || hasContactPhone) {
            binding.tvContactName.text = if (hasContactName) card.emergencyContact.name else "Emergency Contact"
            binding.tvContactPhone.text = if (hasContactPhone) card.emergencyContact.phone else "No phone number"
            binding.btnCallEmergencyContact.isVisible = hasContactPhone

            if (hasContactPhone) {
                binding.btnCallEmergencyContact.setOnClickListener {
                    viewModel.logEmergencyContactDialInitiated()
                    launchDialer(card.emergencyContact.phone)
                }
            }
        } else {
            binding.tvContactName.text = "No emergency contact recorded"
            binding.tvContactPhone.text = "Add an emergency contact in your health profile for quick dialing."
            binding.btnCallEmergencyContact.isVisible = false
        }

        // 3. Known Allergies
        if (card.allergies.allergies.isNotEmpty()) {
            binding.tvAllergiesContent.text = card.allergies.allergies.joinToString("\n") { "• $it" }
        } else if (card.allergies.rawText.isNotBlank()) {
            binding.tvAllergiesContent.text = card.allergies.rawText
        } else {
            binding.tvAllergiesContent.text = "No known allergies recorded."
        }

        // 4. Blood Group
        binding.tvBloodGroup.text = card.bloodGroup.ifBlank { "Not recorded" }

        // 5. Active Medications
        if (card.medications.medications.isNotEmpty()) {
            binding.tvMedicationsContent.text = card.medications.medications.joinToString("\n") { med ->
                val dosagePart = if (med.dosage.isNotBlank()) " (${med.dosage})" else ""
                val freqPart = if (med.frequency.isNotBlank()) " — ${med.frequency}" else ""
                val instPart = if (med.instructions.isNotBlank()) " [${med.instructions}]" else ""
                "• ${med.name}$dosagePart$freqPart$instPart"
            }
        } else if (card.medications.rawFallback.isNotBlank()) {
            binding.tvMedicationsContent.text = card.medications.rawFallback
        } else {
            binding.tvMedicationsContent.text = "No active medications recorded."
        }

        // 6. Medical Conditions
        if (card.conditions.conditions.isNotEmpty()) {
            binding.tvConditionsContent.text = card.conditions.conditions.joinToString("\n") { "• $it" }
        } else if (card.conditions.rawText.isNotBlank()) {
            binding.tvConditionsContent.text = card.conditions.rawText
        } else {
            binding.tvConditionsContent.text = "No medical conditions recorded."
        }

        // 7. Personal Information
        binding.tvPersonalName.text = "Name: ${card.personalInfo.fullName.ifBlank { "Not recorded" }}"
        val dobText = if (card.personalInfo.dateOfBirth.isNotBlank()) {
            val ageText = card.personalInfo.age?.let { " (Age $it)" } ?: ""
            "DOB: ${card.personalInfo.dateOfBirth}$ageText"
        } else {
            "DOB: Not recorded"
        }
        binding.tvPersonalDobAge.text = dobText
        binding.tvPersonalGender.text = "Gender: ${card.personalInfo.gender.ifBlank { "Not recorded" }}"

        // 8. Important Notes
        binding.tvNotesContent.text = card.notes.notes.ifBlank { "No additional health notes recorded." }
    }

    private fun launchDialer(phoneNumber: String) {
        val cleanPhone = phoneNumber.trim()
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanPhone")
        }
        try {
            startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open phone dialer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyShareDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Export Emergency Health Card")
            .setMessage("This emergency card contains your personal health information. Share only with emergency responders or trusted healthcare providers.")
            .setPositiveButton("Export & Share") { _, _ ->
                viewModel.exportEmergencyCardAsText(ctx) { uri ->
                    if (uri != null) {
                        viewModel.logCardShared()
                        launchShareFileIntent(uri)
                    } else {
                        Toast.makeText(ctx, "Failed to prepare emergency card export", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchShareFileIntent(fileUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "MediSense Emergency Health Access Card")
            putExtra(Intent.EXTRA_TEXT, "Here is my MediSense Emergency Health Access Card summary.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(shareIntent, "Share Emergency Health Card")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
