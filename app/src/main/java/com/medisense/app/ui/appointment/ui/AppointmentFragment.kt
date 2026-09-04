package com.medisense.app.ui.appointment.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentAppointmentBinding
import com.medisense.app.ui.appointment.adapter.AppointmentAdapter
import com.medisense.app.ui.appointment.viewmodel.AppointmentFilter
import com.medisense.app.ui.appointment.viewmodel.AppointmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppointmentFragment : Fragment() {

    private var _binding: FragmentAppointmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppointmentViewModel by viewModels()
    private lateinit var adapter: AppointmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppointmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilters()
        setupFab()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppointmentAdapter(
            onCompleteClick = { appt ->
                viewModel.markCompleted(appt.id)
            },
            onCancelClick = { appt ->
                showCancelConfirmationDialog(appt.id, appt.doctorName)
            },
            onDeleteClick = { appt ->
                showDeleteConfirmationDialog(appt.id, appt.doctorName)
            }
        )
        binding.rvAppointments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppointments.adapter = adapter
    }

    private fun setupFilters() {
        binding.cgFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_upcoming -> viewModel.setFilter(AppointmentFilter.UPCOMING)
                R.id.chip_completed -> viewModel.setFilter(AppointmentFilter.COMPLETED)
                R.id.chip_cancelled -> viewModel.setFilter(AppointmentFilter.CANCELLED)
                else -> viewModel.setFilter(AppointmentFilter.ALL)
            }
        }
    }

    private fun setupFab() {
        binding.fabAddAppointment.setOnClickListener {
            findNavController().navigate(R.id.action_appointmentFragment_to_addAppointmentFragment)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.appointments.collect { list ->
                        binding.llEmptyState.isVisible = list.isEmpty()
                        binding.rvAppointments.isVisible = list.isNotEmpty()
                        adapter.submitList(list)
                    }
                }

                launch {
                    viewModel.userMessage.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showCancelConfirmationDialog(id: Long, doctor: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Appointment")
            .setMessage("Are you sure you want to cancel your appointment with $doctor?")
            .setPositiveButton("Cancel Appointment") { _, _ ->
                viewModel.cancelAppointment(id)
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(id: Long, doctor: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Appointment")
            .setMessage("Are you sure you want to delete the appointment record with $doctor?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAppointment(id)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
