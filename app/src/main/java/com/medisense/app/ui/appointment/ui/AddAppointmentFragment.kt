package com.medisense.app.ui.appointment.ui

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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.medisense.app.databinding.FragmentAddAppointmentBinding
import com.medisense.app.ui.appointment.viewmodel.AppointmentViewModel
import com.medisense.app.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class AddAppointmentFragment : Fragment() {

    private var _binding: FragmentAddAppointmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppointmentViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                requireContext(),
                "Appointment saved, but reminder notifications are disabled in settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var appointmentId: Long = -1L
    private var appointmentStatus: String = "SCHEDULED"

    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedHour: Int = 10
    private var selectedMinute: Int = 0

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddAppointmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appointmentId = arguments?.getLong("appointment_id", -1L) ?: -1L

        initDefaultDateTime()
        setupToolbar()
        setupDropdowns()
        setupDateTimePickers()
        setupSaveButton()
        observeViewModel()

        if (appointmentId != -1L) {
            loadExistingAppointment(appointmentId)
        }
    }

    private fun loadExistingAppointment(id: Long) {
        viewModel.getAppointmentById(id) { appt ->
            if (appt == null || _binding == null) return@getAppointmentById
            appointmentStatus = appt.status
            binding.toolbar.title = "Edit Appointment"
            binding.btnSaveAppointment.text = "Update Appointment"

            binding.etDoctorName.setText(appt.doctorName)
            binding.etClinicName.setText(appt.clinicName)
            binding.actvAppointmentType.setText(appt.appointmentType, false)

            val reminderStr = when (appt.reminderMinutesBefore) {
                15 -> "15 minutes before"
                30 -> "30 minutes before"
                60 -> "1 hour before"
                120 -> "2 hours before"
                1440 -> "1 day before"
                else -> "None"
            }
            binding.actvReminder.setText(reminderStr, false)

            if (!appt.notes.isNullOrBlank()) {
                binding.etNotes.setText(appt.notes)
            }

            if (appt.appointmentTimestamp > 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = appt.appointmentTimestamp
                }
                selectedYear = cal.get(Calendar.YEAR)
                selectedMonth = cal.get(Calendar.MONTH)
                selectedDay = cal.get(Calendar.DAY_OF_MONTH)
                selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                selectedMinute = cal.get(Calendar.MINUTE)
                updateDateDisplay()
                updateTimeDisplay()
            }
        }
    }

    private fun initDefaultDateTime() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH)
        selectedDay = cal.get(Calendar.DAY_OF_MONTH)
        selectedHour = cal.get(Calendar.HOUR_OF_DAY)
        selectedMinute = cal.get(Calendar.MINUTE)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupDropdowns() {
        val types = arrayOf(
            "General Checkup",
            "Specialist Consultation",
            "Follow-up",
            "Lab Test",
            "Dental Care",
            "Eye Exam",
            "Vaccination",
            "Other"
        )
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        binding.actvAppointmentType.setAdapter(typeAdapter)
        binding.actvAppointmentType.setText("General Checkup", false)

        val reminders = arrayOf(
            "15 minutes before",
            "30 minutes before",
            "1 hour before",
            "2 hours before",
            "1 day before",
            "None"
        )
        val reminderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reminders)
        binding.actvReminder.setAdapter(reminderAdapter)
        binding.actvReminder.setText("30 minutes before", false)
    }

    private fun setupDateTimePickers() {
        updateDateDisplay()
        updateTimeDisplay()

        binding.etAppointmentDate.setOnClickListener {
            val currentCal = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Appointment Date")
                .setSelection(currentCal.timeInMillis)
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                // MaterialDatePicker returns UTC midnight
                val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = selection
                }
                selectedYear = utcCal.get(Calendar.YEAR)
                selectedMonth = utcCal.get(Calendar.MONTH)
                selectedDay = utcCal.get(Calendar.DAY_OF_MONTH)
                updateDateDisplay()
            }
            picker.show(childFragmentManager, "appt_date_picker")
        }

        binding.etAppointmentTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select Appointment Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                updateTimeDisplay()
            }
            picker.show(childFragmentManager, "appt_time_picker")
        }
    }

    private fun updateDateDisplay() {
        val cal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay)
        }
        binding.etAppointmentDate.setText(dateFormat.format(cal.time))
    }

    private fun updateTimeDisplay() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        binding.etAppointmentTime.setText(timeFormat.format(cal.time))
    }

    private fun setupSaveButton() {
        binding.btnSaveAppointment.setOnClickListener {
            val doctor = binding.etDoctorName.text?.toString()?.trim() ?: ""
            val clinic = binding.etClinicName.text?.toString()?.trim() ?: ""
            val type = binding.actvAppointmentType.text?.toString()?.trim() ?: "General Checkup"
            val reminderText = binding.actvReminder.text?.toString()?.trim() ?: "30 minutes before"
            val notes = binding.etNotes.text?.toString()?.trim() ?: ""

            if (doctor.isBlank()) {
                binding.tilDoctorName.error = "Please enter doctor / provider name"
                return@setOnClickListener
            }
            binding.tilDoctorName.error = null

            if (clinic.isBlank()) {
                binding.tilClinicName.error = "Please enter clinic / hospital name"
                return@setOnClickListener
            }
            binding.tilClinicName.error = null

            val reminderMinutes = when (reminderText) {
                "15 minutes before" -> 15
                "30 minutes before" -> 30
                "1 hour before" -> 60
                "2 hours before" -> 120
                "1 day before" -> 1440
                else -> 0
            }

            val compositeCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedYear)
                set(Calendar.MONTH, selectedMonth)
                set(Calendar.DAY_OF_MONTH, selectedDay)
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val exactTimestamp = compositeCal.timeInMillis
            val dateString = dateFormat.format(compositeCal.time)
            val timeString = timeFormat.format(compositeCal.time)

            if (reminderMinutes > 0 && !PermissionHelper.hasNotificationPermission(requireContext())) {
                notificationPermissionLauncher.launch(PermissionHelper.PERMISSION_POST_NOTIFICATIONS)
            }

            if (appointmentId != -1L) {
                viewModel.updateAppointment(
                    id = appointmentId,
                    doctorName = doctor,
                    clinicName = clinic,
                    appointmentType = type,
                    appointmentDate = dateString,
                    appointmentTime = timeString,
                    appointmentTimestamp = exactTimestamp,
                    reminderMinutesBefore = reminderMinutes,
                    notes = if (notes.isNotBlank()) notes else null,
                    status = appointmentStatus
                ) {
                    findNavController().navigateUp()
                }
            } else {
                viewModel.addAppointment(
                    doctorName = doctor,
                    clinicName = clinic,
                    appointmentType = type,
                    appointmentDate = dateString,
                    appointmentTime = timeString,
                    appointmentTimestamp = exactTimestamp,
                    reminderMinutesBefore = reminderMinutes,
                    notes = if (notes.isNotBlank()) notes else null
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
