package de.bedrockchat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.bedrockchat.R
import de.bedrockchat.databinding.FragmentChatBinding
import de.bedrockchat.ui.token.TokenFragment
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private val adapter by lazy {
        MessageAdapter(
            Markwon.builder(requireContext())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(requireContext()))
                .build()
        )
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.selectImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentChatBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val bottom = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    ).bottom
                    binding.root.updatePadding(bottom = bottom)
                    return insets
                }
            }
        )
        setupRecyclerView()
        setupInput()
        setupMenu()
        observeState()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
    }

    private fun sendCurrentInput() {
        val text = binding.editInput.text?.toString() ?: ""
        viewModel.send(text)
        binding.editInput.text?.clear()
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

        binding.btnDismissError.setOnClickListener { viewModel.clearError() }

        binding.btnAttach.setOnClickListener { pickImage.launch("image/*") }

        binding.btnRemoveImage.setOnClickListener { viewModel.clearPendingImage() }
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
                        val isLoading = state is ChatViewModel.UiState.Loading
                        binding.progressBar.isVisible = isLoading
                        binding.btnSend.isEnabled = !isLoading
                        binding.btnAttach.isEnabled = !isLoading
                        binding.errorBanner.isVisible = state is ChatViewModel.UiState.Error

                        if (state is ChatViewModel.UiState.Error) {
                            binding.errorText.text = state.message
                        }
                    }
                }

                launch {
                    viewModel.pendingImage.collect { pending ->
                        if (pending != null) {
                            binding.imagePreviewContainer.isVisible = true
                            binding.previewImage.setImageURI(pending.uri)
                        } else {
                            binding.imagePreviewContainer.isVisible = false
                            binding.previewImage.setImageURI(null)
                        }
                    }
                }

                launch {
                    viewModel.navigateToToken.collect {
                        val args = Bundle().apply {
                            putString(TokenFragment.ARG_ERROR, getString(R.string.error_token_invalid))
                        }
                        findNavController().navigate(R.id.action_chat_to_token, args)
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
