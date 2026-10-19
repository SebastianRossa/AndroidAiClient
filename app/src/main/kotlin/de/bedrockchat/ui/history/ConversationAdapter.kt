package de.bedrockchat.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.bedrockchat.data.local.ConversationEntity
import de.bedrockchat.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onDelete: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConversationEntity) {
            binding.textConvTitle.text = item.title
            binding.textConvDate.text = formatDate(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }
            binding.btnDeleteConv.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private fun formatDate(epochMillis: Long): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMillis))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(a: ConversationEntity, b: ConversationEntity) =
                a.id == b.id

            override fun areContentsTheSame(a: ConversationEntity, b: ConversationEntity) =
                a == b
        }
    }
}
