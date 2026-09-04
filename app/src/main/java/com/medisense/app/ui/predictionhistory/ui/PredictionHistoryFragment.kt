package com.medisense.app.ui.predictionhistory.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentPredictionHistoryBinding
import com.medisense.app.ui.predictionhistory.viewmodel.PredictionHistoryUiState
import com.medisense.app.ui.predictionhistory.viewmodel.PredictionHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PredictionHistoryFragment : Fragment() {

    private var _binding: FragmentPredictionHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PredictionHistoryViewModel by viewModels()
    private lateinit var adapter: PredictionHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete_all -> {
                    showDeleteAllConfirmationDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = PredictionHistoryAdapter(
            onItemClick = { record ->
                val bundle = bundleOf("recordId" to record.id)
                findNavController().navigate(
                    R.id.action_predictionHistoryFragment_to_predictionHistoryDetailFragment,
                    bundle
                )
            },
            onDeleteClick = { record ->
                showDeleteSingleConfirmationDialog(record.id)
            }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyState.collect { state ->
                    when (state) {
                        is PredictionHistoryUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.rvHistory.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.GONE
                        }
                        is PredictionHistoryUiState.Empty -> {
                            binding.progressBar.visibility = View.GONE
                            binding.rvHistory.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.VISIBLE
                        }
                        is PredictionHistoryUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvHistory.visibility = View.VISIBLE
                            adapter.submitList(state.history)
                        }
                        is PredictionHistoryUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteSingleConfirmationDialog(recordId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this prediction record?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteHistoryItem(recordId)
                Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteAllConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All History")
            .setMessage("Are you sure you want to delete all prediction history? This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                viewModel.deleteAllHistory()
                Toast.makeText(requireContext(), "All history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
