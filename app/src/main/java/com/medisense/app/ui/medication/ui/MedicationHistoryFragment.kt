package com.medisense.app.ui.medication.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.medisense.app.R
import com.medisense.app.databinding.FragmentMedicationHistoryBinding
import com.medisense.app.ui.medication.adapter.MedicationHistoryAdapter
import com.medisense.app.ui.medication.viewmodel.MedicationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MedicationHistoryFragment : Fragment() {

    private var _binding: FragmentMedicationHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedicationViewModel by viewModels()
    private lateinit var adapter: MedicationHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilters()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = MedicationHistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.cgFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_taken -> "TAKEN"
                R.id.chip_filter_skipped -> "SKIPPED"
                R.id.chip_filter_missed -> "MISSED"
                else -> null
            }
            val currentList = viewModel.history.value
            if (filter != null) {
                adapter.submitList(currentList.filter { it.status.equals(filter, ignoreCase = true) })
            } else {
                adapter.submitList(currentList)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.history.collect { list ->
                    binding.layoutEmptyHistory.isVisible = list.isEmpty()
                    binding.rvHistory.isVisible = list.isNotEmpty()
                    adapter.submitList(list)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
