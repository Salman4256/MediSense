package com.medisense.app.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.medisense.app.R
import com.medisense.app.databinding.FragmentSplashBinding
import com.medisense.app.ui.auth.viewmodel.AuthState
import com.medisense.app.ui.auth.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Authenticated -> {
                            findNavController().navigate(R.id.action_splashFragment_to_dashboardFragment)
                        }
                        is AuthState.Unauthenticated -> {
                            findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
                        }
                        else -> {
                            // Loading or Idle
                        }
                    }
                }
            }
        }

        // Give it a small delay for branding
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1000)
            authViewModel.checkSession()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
