package com.medisense.app.ui.prediction.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.medisense.app.R
import com.medisense.app.data.model.Symptom
import com.medisense.app.databinding.FragmentDiseasePredictionBinding
import com.medisense.app.ui.prediction.viewmodel.DiseasePredictionViewModel
import com.medisense.app.ui.prediction.viewmodel.PredictionUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DiseasePredictionFragment : Fragment() {

    private var _binding: FragmentDiseasePredictionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiseasePredictionViewModel by viewModels()
    private lateinit var adapter: SymptomAdapter
    private var allSymptoms: List<Symptom> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiseasePredictionBinding.inflate(inflater, container, false)
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
        adapter = SymptomAdapter { symptom, isSelected ->
            if (isSelected) {
                viewModel.selectSymptom(symptom)
            } else {
                viewModel.deselectSymptom(symptom)
            }
        }
        binding.rvSymptoms.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSymptoms.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnPredict.setOnClickListener {
            viewModel.runPrediction()
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearSelections()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSymptoms(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterSymptoms(query: String) {
        val filtered = if (query.isBlank()) {
            allSymptoms
        } else {
            val q = query.lowercase().trim()
            allSymptoms.filter { it.displayName.lowercase().contains(q) }
        }
        adapter.submitList(filtered)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.symptoms.collect { list ->
                        allSymptoms = list
                        filterSymptoms(binding.etSearch.text.toString())
                    }
                }

                launch {
                    viewModel.selectedSymptoms.collect { selected ->
                        adapter.updateSelected(selected)
                        binding.tvSelectionCount.text = "${selected.size} Symptoms Selected"
                        binding.btnPredict.isEnabled = selected.isNotEmpty()
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is PredictionUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.btnPredict.isEnabled = false
                            }
                            is PredictionUiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.btnPredict.isEnabled = true
                                viewModel.resetState()

                                // Navigate to results fragment
                                val bundle = Bundle().apply {
                                    putSerializable("predictions", ArrayList(state.predictions))
                                    putSerializable("selectedSymptoms", ArrayList(viewModel.selectedSymptoms.value))
                                }
                                findNavController().navigate(
                                    R.id.action_diseasePredictionFragment_to_predictionResultFragment,
                                    bundle
                                )
                            }
                            is PredictionUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.btnPredict.isEnabled = true
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                                viewModel.resetState()
                            }
                            is PredictionUiState.Idle -> {
                                binding.progressBar.visibility = View.GONE
                            }
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
