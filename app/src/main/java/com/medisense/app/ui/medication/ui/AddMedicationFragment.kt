package com.medisense.app.ui.medication.ui

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
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.medisense.app.R
import com.medisense.app.databinding.FragmentAddMedicationBinding
import com.medisense.app.ui.medication.viewmodel.MedicationViewModel
import com.medisense.app.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddMedicationFragment : Fragment() {

    private var _binding: FragmentAddMedicationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedicationViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                requireContext(),
                "Reminder saved, but notifications are disabled in settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var medicationId: Long = -1L
    private var medicationActive: Boolean = true

    private val scheduledTimesList = mutableListOf<String>()
    private var startDateTimestamp: Long = System.currentTimeMillis()
    private var endDateTimestamp: Long? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMedicationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        medicationId = arguments?.getLong("medication_id", -1L) ?: -1L

        setupToolbar()
        setupDropdowns()
        setupTimePickers()
        setupDatePickers()
        setupSaveButton()
        observeViewModel()

        if (medicationId != -1L) {
            loadExistingMedication(medicationId)
        }
    }

    private fun loadExistingMedication(id: Long) {
        viewModel.getMedicationById(id) { med ->
            if (med == null || _binding == null) return@getMedicationById
            medicationActive = med.active
            binding.toolbar.title = "Edit Medication"
            binding.btnSaveMedication.text = "Update Medication"

            binding.etName.setText(med.medicineName)
            binding.etDosage.setText(med.dosage)
            binding.actvDosageUnit.setText(med.dosageUnit, false)

            val freqDisplay = when (med.frequency) {
                "ONCE_DAILY" -> "Once Daily"
                "TWICE_DAILY" -> "Twice Daily"
                "THREE_TIMES_DAILY" -> "Three Times Daily"
                "FOUR_TIMES_DAILY" -> "Four Times Daily"
                else -> "As Needed"
            }
            binding.actvFrequency.setText(freqDisplay, false)

            scheduledTimesList.clear()
            scheduledTimesList.addAll(med.scheduledTimes)
            renderTimeChips()

            startDateTimestamp = med.startDate
            binding.btnStartDate.text = "Start: ${dateFormat.format(Date(startDateTimestamp))}"

            endDateTimestamp = med.endDate
            if (med.endDate != null) {
                binding.btnEndDate.text = "End: ${dateFormat.format(Date(med.endDate))}"
            }

            binding.etInstructions.setText(med.instructions)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupDropdowns() {
        val dosageUnits = arrayOf("tablet", "capsule", "mg", "ml", "drops", "pills", "tsp", "puff")
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dosageUnits)
        binding.actvDosageUnit.setAdapter(unitAdapter)

        val frequencies = arrayOf(
            "Once Daily",
            "Twice Daily",
            "Three Times Daily",
            "Four Times Daily",
            "As Needed"
        )
        val freqAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies)
        binding.actvFrequency.setAdapter(freqAdapter)
        binding.actvFrequency.setText("Once Daily", false)

        binding.actvFrequency.setOnItemClickListener { _, _, position, _ ->
            when (frequencies[position]) {
                "Once Daily" -> setPresetTimes(listOf("08:00 AM"))
                "Twice Daily" -> setPresetTimes(listOf("08:00 AM", "08:00 PM"))
                "Three Times Daily" -> setPresetTimes(listOf("08:00 AM", "02:00 PM", "08:00 PM"))
                "Four Times Daily" -> setPresetTimes(listOf("08:00 AM", "12:00 PM", "04:00 PM", "08:00 PM"))
            }
        }

        if (medicationId == -1L) {
            setPresetTimes(listOf("08:00 AM"))
        }
    }

    private fun setPresetTimes(times: List<String>) {
        scheduledTimesList.clear()
        scheduledTimesList.addAll(times)
        renderTimeChips()
    }

    private fun setupTimePickers() {
        binding.btnAddTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(8)
                .setMinute(0)
                .setTitleText("Select Reminder Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, picker.hour)
                    set(Calendar.MINUTE, picker.minute)
                }
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formatted = timeFormat.format(cal.time)
                if (!scheduledTimesList.contains(formatted)) {
                    scheduledTimesList.add(formatted)
                    renderTimeChips()
                }
            }

            picker.show(childFragmentManager, "time_picker")
        }

        binding.btnQuickTest.setOnClickListener {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
            }
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val testTime = timeFormat.format(cal.time)
            if (!scheduledTimesList.contains(testTime)) {
                scheduledTimesList.add(testTime)
                renderTimeChips()
            }
            Toast.makeText(requireContext(), "Test reminder scheduled for $testTime", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderTimeChips() {
        binding.cgReminderTimes.removeAllViews()
        for (time in scheduledTimesList) {
            val chip = Chip(requireContext()).apply {
                text = time
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    scheduledTimesList.remove(time)
                    renderTimeChips()
                }
            }
            binding.cgReminderTimes.addView(chip)
        }
    }

    private fun setupDatePickers() {
        binding.btnStartDate.text = "Start: ${dateFormat.format(Date(startDateTimestamp))}"

        binding.btnStartDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Start Date")
                .setSelection(startDateTimestamp)
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                startDateTimestamp = selection
                binding.btnStartDate.text = "Start: ${dateFormat.format(Date(selection))}"
            }
            picker.show(childFragmentManager, "start_date_picker")
        }

        binding.btnEndDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select End Date")
                .setSelection(endDateTimestamp ?: System.currentTimeMillis())
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                endDateTimestamp = selection
                binding.btnEndDate.text = "End: ${dateFormat.format(Date(selection))}"
            }
            picker.show(childFragmentManager, "end_date_picker")
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveMedication.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim() ?: ""
            val dosage = binding.etDosage.text?.toString()?.trim() ?: ""
            val unit = binding.actvDosageUnit.text?.toString()?.trim() ?: "tablet"
            val freqRaw = binding.actvFrequency.text?.toString()?.trim() ?: "Once Daily"
            val instructions = binding.etInstructions.text?.toString()?.trim() ?: ""

            if (name.isBlank()) {
                binding.tilName.error = "Please enter medication name"
                return@setOnClickListener
            }
            binding.tilName.error = null

            if (dosage.isBlank()) {
                binding.tilDosage.error = "Enter dosage"
                return@setOnClickListener
            }
            binding.tilDosage.error = null

            val frequencyKey = when (freqRaw) {
                "Once Daily" -> "ONCE_DAILY"
                "Twice Daily" -> "TWICE_DAILY"
                "Three Times Daily" -> "THREE_TIMES_DAILY"
                "Four Times Daily" -> "FOUR_TIMES_DAILY"
                else -> "AS_NEEDED"
            }

            val times = if (scheduledTimesList.isNotEmpty()) scheduledTimesList else listOf("08:00 AM")

            // Check notification permission just-in-time on Android 13+
            if (!PermissionHelper.hasNotificationPermission(requireContext())) {
                notificationPermissionLauncher.launch(PermissionHelper.PERMISSION_POST_NOTIFICATIONS)
            }

            if (medicationId != -1L) {
                viewModel.updateMedication(
                    id = medicationId,
                    name = name,
                    dosage = dosage,
                    unit = unit,
                    frequency = frequencyKey,
                    scheduledTimes = times,
                    startDate = startDateTimestamp,
                    endDate = endDateTimestamp,
                    instructions = instructions,
                    active = medicationActive
                ) {
                    findNavController().navigateUp()
                }
            } else {
                viewModel.addMedication(
                    name = name,
                    dosage = dosage,
                    unit = unit,
                    frequency = frequencyKey,
                    scheduledTimes = times,
                    startDate = startDateTimestamp,
                    endDate = endDateTimestamp,
                    instructions = instructions
                ) {
                    findNavController().navigateUp()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userMessage.collect { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
