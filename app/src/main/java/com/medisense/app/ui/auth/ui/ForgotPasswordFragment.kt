package com.medisense.app.ui.auth.ui

import android.os.Bundle
import android.util.Patterns
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
import com.medisense.app.databinding.FragmentForgotPasswordBinding
import com.medisense.app.ui.auth.viewmodel.AuthState
import com.medisense.app.ui.auth.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeAuthState()
    }

    private fun setupListeners() {
        binding.btnReset.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()

            if (validateInput(email)) {
                authViewModel.resetPassword(email)
            }
        }
    }

    private fun validateInput(email: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Invalid email format"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        return isValid
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnReset.isEnabled = false
                        }
                        is AuthState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnReset.isEnabled = true
                            Toast.makeText(
                                requireContext(),
                                "Password reset email sent.",
                                Toast.LENGTH_LONG
                            ).show()
                            authViewModel.resetState()
                            findNavController().navigateUp()
                        }
                        is AuthState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnReset.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
                        }
                        else -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnReset.isEnabled = true
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
