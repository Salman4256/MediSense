package com.medisense.app.ui.assistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.R
import com.medisense.app.databinding.FragmentChatHistoryBinding
import com.medisense.app.ui.assistant.adapter.ChatHistoryAdapter
import com.medisense.app.ui.assistant.viewmodel.HealthAssistantViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatHistoryFragment : Fragment() {

    private var _binding: FragmentChatHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthAssistantViewModel by activityViewModels()
    private lateinit var adapter: ChatHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        viewModel.loadConversations()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_new_chat -> {
                    viewModel.startNewConversation()
                    findNavController().navigateUp()
                    true
                }
                R.id.action_clear_all -> {
                    showClearAllConfirmationDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatHistoryAdapter(
            onChatClick = { conversation ->
                viewModel.selectConversation(conversation.id)
                findNavController().navigateUp()
            },
            onDeleteClick = { conversation ->
                showDeleteConfirmationDialog(conversation.id)
            }
        )
        binding.rvChatHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatHistory.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { list ->
                    binding.tvEmpty.isVisible = list.isEmpty()
                    binding.rvChatHistory.isVisible = list.isNotEmpty()
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(conversationId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete this chat session?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteConversation(conversationId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All History")
            .setMessage("Are you sure you want to delete all chat history? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                viewModel.clearAllConversations()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
