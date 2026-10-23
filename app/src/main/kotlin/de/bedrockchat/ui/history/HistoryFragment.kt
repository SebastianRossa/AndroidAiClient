package de.bedrockchat.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.bedrockchat.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.factory(requireActivity().application) }

    private val adapter by lazy {
        ConversationAdapter(
            onClick = { conversation ->
                setFragmentResult(
                    RESULT_KEY,
                    Bundle().apply { putLong(ARG_CONVERSATION_ID, conversation.id) }
                )
                findNavController().navigateUp()
            },
            onDelete = { conversation ->
                viewModel.deleteConversation(conversation.id)
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentHistoryBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // AppBarLayout (fitsSystemWindows=true) konsumiert den Top-Inset → hier nur Bottom.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            v.updatePadding(
                bottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.recyclerHistory.adapter = adapter
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { list ->
                    // previousFirstId VOR submitList sichern: Der Commit-Callback läuft
                    // erst nachdem DiffUtil alle Änderungen in den RecyclerView eingespielt
                    // hat – zu diesem Zeitpunkt hat sich currentList bereits aktualisiert.
                    val previousFirstId = adapter.currentList.firstOrNull()?.id

                    adapter.submitList(list) {
                        // scrollToPositionWithOffset(0, 0) ist zuverlässiger als
                        // scrollToPosition(0): Es positioniert Item 0 exakt am oberen
                        // Rand des RecyclerView, unabhängig von laufenden Item-Animationen.
                        if (shouldScrollToTop(previousFirstId, list)) {
                            (binding.recyclerHistory.layoutManager as? LinearLayoutManager)
                                ?.scrollToPositionWithOffset(0, 0)
                        }
                    }
                    binding.textEmpty.isVisible = list.isEmpty()
                    binding.recyclerHistory.isVisible = list.isNotEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "history_result"
        const val ARG_CONVERSATION_ID = "conversationId"
    }
}
