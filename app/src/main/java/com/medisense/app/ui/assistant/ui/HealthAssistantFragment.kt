package com.medisense.app.ui.assistant.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentHealthAssistantBinding
import com.medisense.app.ui.assistant.adapter.ChatMessageAdapter
import com.medisense.app.ui.assistant.viewmodel.ChatUiState
import com.medisense.app.ui.assistant.viewmodel.HealthAssistantViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

@AndroidEntryPoint
class HealthAssistantFragment : Fragment() {

    private var _binding: FragmentHealthAssistantBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthAssistantViewModel by activityViewModels()
    private lateinit var adapter: ChatMessageAdapter

    private var selectedImageUri: Uri? = null
    private var selectedImageBase64: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { handleBitmapImage(it) }
        }
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                binding.etMessage.setText(spokenText)
            }
        }
        binding.llVoiceStatus.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSuggestions()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.toolbar.inflateMenu(R.menu.menu_health_assistant)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_history -> {
                    findNavController().navigate(R.id.action_healthAssistantFragment_to_chatHistoryFragment)
                    true
                }
                R.id.action_clear -> {
                    showClearConfirmationDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter()
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChat.layoutManager = layoutManager
        binding.rvChat.adapter = adapter
    }

    private fun setupSuggestions() {
        val suggestions = listOf(
            "How to manage high blood pressure?",
            "What causes frequent migraines?",
            "Tips for better sleep hygiene",
            "How to lower cholesterol naturally?",
            "When should I consult a doctor for a cough?"
        )

        binding.cgSuggestions.removeAllViews()
        for (text in suggestions) {
            val chip = Chip(requireContext()).apply {
                this.text = text
                isCheckable = false
                setOnClickListener {
                    binding.etMessage.setText(text)
                    sendMessage()
                }
            }
            binding.cgSuggestions.addView(chip)
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnCamera.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                cameraLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Camera is not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMic.setOnClickListener {
            startVoiceInput()
        }

        binding.btnCancelVoice.setOnClickListener {
            binding.llVoiceStatus.visibility = View.GONE
        }

        binding.btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: ""
        if (text.isBlank() && selectedImageBase64 == null) return

        val imgUriStr = selectedImageUri?.toString()
        val imgBase64 = selectedImageBase64

        binding.etMessage.text?.clear()
        clearSelectedImage()

        viewModel.sendMessage(
            prompt = text,
            imageUri = imgUriStr,
            imageBase64 = imgBase64
        )
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            selectedImageUri = uri
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                selectedImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                binding.cardImagePreview.visibility = View.VISIBLE
                binding.ivPreview.setImageURI(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBitmapImage(bitmap: Bitmap) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            selectedImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            binding.cardImagePreview.visibility = View.VISIBLE
            binding.ivPreview.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearSelectedImage() {
        selectedImageUri = null
        selectedImageBase64 = null
        binding.cardImagePreview.visibility = View.GONE
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your health question...")
        }
        try {
            binding.llVoiceStatus.visibility = View.VISIBLE
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            binding.llVoiceStatus.visibility = View.GONE
            Toast.makeText(requireContext(), "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatState.collect { state ->
                        when (state) {
                            is ChatUiState.Success -> {
                                adapter.isTyping = state.isTyping
                                adapter.submitList(state.messages) {
                                    if (state.messages.isNotEmpty() || state.isTyping) {
                                        binding.rvChat.smoothScrollToPosition(adapter.itemCount - 1)
                                    }
                                }
                                binding.hsvSuggestions.isVisible = state.messages.isEmpty()
                            }
                            is ChatUiState.Loading -> {
                                adapter.isTyping = true
                            }
                            is ChatUiState.Error -> {
                                adapter.isTyping = false
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.errorMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to start a new chat session?")
            .setPositiveButton("New Chat") { _, _ ->
                viewModel.startNewConversation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
