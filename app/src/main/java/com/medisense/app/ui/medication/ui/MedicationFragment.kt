package com.medisense.app.ui.medication.ui

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
import com.medisense.app.databinding.FragmentMedicationBinding
import com.medisense.app.ui.medication.adapter.MedicationAdapter
import com.medisense.app.ui.medication.viewmodel.AdherencePeriod
import com.medisense.app.ui.medication.viewmodel.MedicationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MedicationFragment : Fragment() {

    private var _binding: FragmentMedicationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedicationViewModel by viewModels()
    private lateinit var adapter: MedicationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = MedicationAdapter(
            onToggleActive = { med, active ->
                viewModel.toggleActive(med.id, active)
            },
            onMarkTaken = { med ->
                viewModel.markTaken(med)
            },
            onMarkSkipped = { med ->
                viewModel.markSkipped(med)
            },
            onEdit = { med ->
                findNavController().navigate(
                    R.id.action_medicationListFragment_to_addEditMedicationFragment,
                    androidx.core.os.bundleOf("medication_id" to med.id)
                )
            },
            onDelete = { med ->
                showDeleteConfirmationDialog(med.id, med.medicineName)
            }
        )
        binding.rvMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMedications.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddMedication.setOnClickListener {
            findNavController().navigate(R.id.action_medicationListFragment_to_addEditMedicationFragment)
        }

        binding.btnViewHistory.setOnClickListener {
            findNavController().navigate(R.id.action_medicationListFragment_to_medicationHistoryFragment)
        }

        binding.cgAdherencePeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_period_today -> viewModel.setAdherencePeriod(AdherencePeriod.TODAY)
                R.id.chip_period_weekly -> viewModel.setAdherencePeriod(AdherencePeriod.LAST_7_DAYS)
                R.id.chip_period_overall -> viewModel.setAdherencePeriod(AdherencePeriod.OVERALL)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.medications.collect { list ->
                        binding.layoutEmptyState.isVisible = list.isEmpty()
                        binding.rvMedications.isVisible = list.isNotEmpty()
                        adapter.submitList(list)
                    }
                }

                launch {
                    viewModel.adherenceStats.collect { stats ->
                        val percentInt = stats.percentage.toInt()
                        binding.tvAdherencePercent.text = "$percentInt%"
                        binding.progressAdherence.progress = percentInt
                        binding.tvAdherenceSummaryDetail.text = "Taken ${stats.takenCount} of ${stats.totalScheduled} scheduled doses"
                        binding.tvTakenStat.text = "Taken: ${stats.takenCount}"
                        binding.tvSkippedStat.text = "Skipped: ${stats.skippedCount}"
                        binding.tvMissedStat.text = "Missed: ${stats.missedCount}"
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

    private fun showDeleteConfirmationDialog(id: Long, name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Medication")
            .setMessage("Are you sure you want to delete $name?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMedication(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
