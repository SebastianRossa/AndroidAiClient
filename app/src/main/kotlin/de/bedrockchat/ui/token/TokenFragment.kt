package de.bedrockchat.ui.token

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import de.bedrockchat.Defaults
import de.bedrockchat.R
import de.bedrockchat.databinding.FragmentTokenBinding

class TokenFragment : Fragment() {

    private var _binding: FragmentTokenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TokenViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentTokenBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editRegion.setText(viewModel.region)
        binding.editModel.setText(viewModel.modelId)

        arguments?.getString(ARG_ERROR)?.takeIf { it.isNotBlank() }?.let {
            binding.errorText.text = it
            binding.errorText.isVisible = true
        }

        binding.editToken.doAfterTextChanged {
            binding.btnSave.isEnabled = !it.isNullOrBlank()
        }

        binding.btnSave.setOnClickListener {
            viewModel.save(
                token   = binding.editToken.text.toString(),
                region  = binding.editRegion.text.toString().ifBlank { Defaults.REGION },
                modelId = binding.editModel.text.toString().ifBlank { Defaults.MODEL_ID }
            )
            binding.editToken.text?.clear()
            findNavController().navigate(R.id.action_token_to_chat)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        const val ARG_ERROR = "error"
    }
}
