package com.medisense.app.ui.onboarding

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.medisense.app.R
import com.medisense.app.databinding.FragmentFirstLaunchBinding
import com.medisense.app.utils.SupportIntentHelper
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

        setupConsentSection()
        setupGetStartedAction()
    }

    private fun setupConsentSection() {
        // Restore or initialize checkbox state
        val alreadyAccepted = viewModel.hasAcceptedPolicyConsent()
        binding.cbConsent.isChecked = alreadyAccepted
        binding.btnGetStarted.isEnabled = alreadyAccepted

        binding.cbConsent.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAcceptedPolicyConsent(isChecked)
            binding.btnGetStarted.isEnabled = isChecked
        }

        // Configure clickable Terms & Conditions and Privacy Policy links
        val fullText = getString(R.string.consent_terms_and_privacy)
        val termsText = getString(R.string.terms_and_conditions)
        val privacyText = getString(R.string.privacy_policy)

        val spannable = SpannableStringBuilder(fullText)
        val primaryColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

        val termsIndex = fullText.indexOf(termsText)
        if (termsIndex != -1) {
            val termsSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    SupportIntentHelper.openOfficialWebsite(requireContext())
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = primaryColor
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(termsSpan, termsIndex, termsIndex + termsText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val privacyIndex = fullText.indexOf(privacyText)
        if (privacyIndex != -1) {
            val privacySpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    SupportIntentHelper.openOfficialWebsite(requireContext())
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = primaryColor
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(privacySpan, privacyIndex, privacyIndex + privacyText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.tvConsent.text = spannable
        binding.tvConsent.movementMethod = LinkMovementMethod.getInstance()
        binding.tvConsent.highlightColor = Color.TRANSPARENT
    }

    private fun setupGetStartedAction() {
        binding.btnGetStarted.setOnClickListener {
            // Guard: Explicit consent checkbox must be checked
            if (!binding.cbConsent.isChecked) {
                return@setOnClickListener
            }

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
