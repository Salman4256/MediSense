package com.medisense.app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.medisense.app.R
import com.medisense.app.databinding.FragmentFirstLaunchBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FirstLaunchFragment : Fragment() {

    private var _binding: FragmentFirstLaunchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirstLaunchViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstLaunchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGetStarted.setOnClickListener {
            viewModel.completeOnboarding()
            if (viewModel.isUserLoggedIn()) {
                findNavController().navigate(R.id.action_firstLaunchFragment_to_dashboardFragment)
            } else {
                findNavController().navigate(R.id.action_firstLaunchFragment_to_loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
