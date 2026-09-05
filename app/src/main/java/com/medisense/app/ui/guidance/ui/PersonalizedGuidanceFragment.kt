package com.medisense.app.ui.guidance.ui

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
import com.medisense.app.R
import com.medisense.app.databinding.FragmentPersonalizedGuidanceBinding
import com.medisense.app.domain.model.GuidanceActionType
import com.medisense.app.domain.model.GuidanceEngineResult
import com.medisense.app.domain.model.PersonalizedGuidance
import com.medisense.app.ui.guidance.adapter.PersonalizedGuidanceAdapter
import com.medisense.app.ui.guidance.viewmodel.PersonalizedGuidanceUiState
import com.medisense.app.ui.guidance.viewmodel.PersonalizedGuidanceViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PersonalizedGuidanceFragment : Fragment() {

    private var _binding: FragmentPersonalizedGuidanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PersonalizedGuidanceViewModel by viewModels()
    private val guidanceAdapter by lazy {
        PersonalizedGuidanceAdapter { item ->
            handleGuidanceAction(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizedGuidanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        binding.rvGuidanceItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGuidanceItems.adapter = guidanceAdapter
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PersonalizedGuidanceUiState.Loading -> {
                            binding.progressBar.isVisible = true
                            binding.scrollView.isVisible = false
                        }
                        is PersonalizedGuidanceUiState.Success -> {
                            binding.progressBar.isVisible = false
                            binding.scrollView.isVisible = true
                            renderGuidanceResult(state.result)
                        }
                        is PersonalizedGuidanceUiState.Empty -> {
                            binding.progressBar.isVisible = false
                            binding.scrollView.isVisible = true
                            binding.rvGuidanceItems.isVisible = false
                            binding.layoutEmptyState.isVisible = true
                            binding.tvEmptyMessage.text = state.message
                            binding.cardDataLimitations.isVisible = false
                        }
                        is PersonalizedGuidanceUiState.Error -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderGuidanceResult(result: GuidanceEngineResult) {
        binding.layoutEmptyState.isVisible = false
        binding.rvGuidanceItems.isVisible = true
        guidanceAdapter.submitList(result.guidanceList)

        if (!result.dataLimitationsNotice.isNullOrBlank()) {
            binding.cardDataLimitations.isVisible = true
            binding.tvDataLimitations.text = result.dataLimitationsNotice
        } else {
            binding.cardDataLimitations.isVisible = false
        }
    }

    private fun handleGuidanceAction(guidance: PersonalizedGuidance) {
        try {
            when (guidance.actionType) {
                GuidanceActionType.NAVIGATE_PROFILE -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_profileFragment)
                }
                GuidanceActionType.NAVIGATE_MEDICATIONS -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_medicationListFragment)
                }
                GuidanceActionType.NAVIGATE_APPOINTMENTS -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_appointmentFragment)
                }
                GuidanceActionType.NAVIGATE_PREDICTIONS -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_predictionHistoryFragment)
                }
                GuidanceActionType.NAVIGATE_TRENDS -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_longitudinalHealthFragment)
                }
                GuidanceActionType.NAVIGATE_RCHR -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_rchrFragment)
                }
                GuidanceActionType.NAVIGATE_RISK -> {
                    findNavController().navigate(R.id.action_personalizedGuidanceFragment_to_contextualRiskFragment)
                }
                GuidanceActionType.NONE -> {}
            }
        } catch (e: Exception) {
            // Graceful fallback in case of navigation error
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
