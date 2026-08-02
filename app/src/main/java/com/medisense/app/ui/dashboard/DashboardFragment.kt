package com.medisense.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.medisense.app.R
import com.medisense.app.databinding.FragmentDashboardBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupHealthRecordsButton()
        setupDiseasePredictionButton()
        setupHealthAssistantButton()
        setupProfileNavigation()
    }

    private fun setupProfileNavigation() {
        binding.tvWelcome.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
        }
    }

    private fun setupHealthRecordsButton() {
        binding.btnHealthRecords.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_healthRecordFragment)
        }
    }

    private fun setupDiseasePredictionButton() {
        binding.btnDiseasePrediction.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_predictionFragment)
        }
    }

    private fun setupHealthAssistantButton() {
        binding.btnHealthAssistant.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_healthAssistantFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
