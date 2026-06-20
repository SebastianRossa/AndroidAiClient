package de.bedrockchat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.bedrockchat.R
import de.bedrockchat.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private val adapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentChatBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupInput()
        setupMenu()
        observeState()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            this.adapter = this@ChatFragment.adapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    private fun sendCurrentInput() {
        val text = binding.editInput.text?.toString() ?: return
        if (text.isNotBlank()) {
            viewModel.send(text)
            binding.editInput.text?.clear()
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener { sendCurrentInput() }

        binding.editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.chat_menu, menu)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_clear -> { viewModel.clearConversation(); true }
                    R.id.action_settings -> {
                        findNavController().navigate(R.id.action_chat_to_settings)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            if (adapter.itemCount > 0) {
                                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                            }
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is ChatViewModel.UiState.Loading
                        binding.btnSend.isEnabled = state !is ChatViewModel.UiState.Loading
                        binding.errorBanner.isVisible = state is ChatViewModel.UiState.Error

                        if (state is ChatViewModel.UiState.Error) {
                            binding.errorText.text = state.message
                        }
                    }
                }

                launch {
                    viewModel.navigateToToken.collect {
                        val args = Bundle().apply {
                            putString("error", getString(R.string.error_token_invalid))
                        }
                        findNavController().navigate(R.id.action_chat_to_token, args)
                    }
                }
            }
        }

        binding.btnDismissError.setOnClickListener { viewModel.clearError() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
