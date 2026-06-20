package de.bedrockchat.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.bedrockchat.data.model.Message
import de.bedrockchat.databinding.ItemMessageAssistantBinding
import de.bedrockchat.databinding.ItemMessageUserBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_USER = 0
        private const val VIEW_ASSISTANT = 1

        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    private lateinit var markwon: Markwon

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == "user") VIEW_USER else VIEW_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_USER) {
            UserViewHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            if (!::markwon.isInitialized) {
                markwon = Markwon.builder(parent.context)
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(parent.context))
                    .build()
            }
            AssistantViewHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false), markwon)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(msg)
            is AssistantViewHolder -> holder.bind(msg)
        }
    }

    class UserViewHolder(private val b: ItemMessageUserBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) { b.messageText.text = msg.text }
    }

    class AssistantViewHolder(
        private val b: ItemMessageAssistantBinding,
        private val markwon: Markwon
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) { markwon.setMarkdown(b.messageText, msg.text) }
    }
}
