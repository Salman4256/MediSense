package com.medisense.app.ui.prediction.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.medisense.app.R
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.Symptom
import com.medisense.app.databinding.FragmentPredictionResultBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PredictionResultFragment : Fragment() {

    private var _binding: FragmentPredictionResultBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PredictionResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        loadData()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = PredictionResultAdapter()
        binding.rvPredictions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPredictions.adapter = adapter
    }

    private fun loadData() {
        // Retrieve arguments
        val predictions = arguments?.getSerializable("predictions") as? ArrayList<DiseasePrediction>
        val symptoms = arguments?.getSerializable("selectedSymptoms") as? ArrayList<Symptom>

        if (!predictions.isNullOrEmpty()) {
            // Render Primary Prediction
            val primary = predictions.first()
            binding.tvTopDisease.text = primary.diseaseName
            binding.tvTopProbability.text = String.format("%.0f%% Probability", primary.probability * 100)

            // Render Secondary Predictions
            if (predictions.size > 1) {
                val secondary = predictions.subList(1, predictions.size)
                adapter.submitList(secondary)
            }
        }

        // Render Symptoms Chips
        if (!symptoms.isNullOrEmpty()) {
            binding.chipGroupSymptoms.removeAllViews()
            for (symptom in symptoms) {
                val chip = Chip(requireContext()).apply {
                    text = symptom.displayName
                    isCheckable = false
                    isClickable = false
                }
                binding.chipGroupSymptoms.addView(chip)
            }
        }
    }

    private fun setupListeners() {
        binding.btnDone.setOnClickListener {
            // Pop back to the Dashboard
            findNavController().popBackStack(R.id.dashboardFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
