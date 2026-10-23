package de.bedrockchat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.databinding.ItemMessageAssistantBinding
import de.bedrockchat.databinding.ItemMessageUserBinding
import io.noties.markwon.Markwon

class MessageAdapter(private val markwon: Markwon) : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    private interface BindableHolder { fun bind(msg: Message) }

    companion object {
        private const val VIEW_USER = 0
        private const val VIEW_ASSISTANT = 1

        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == MessageRole.USER) VIEW_USER else VIEW_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_USER)
            UserViewHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        else
            AssistantViewHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false), markwon)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as BindableHolder).bind(getItem(position))
    }

    private class UserViewHolder(private val b: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(b.root), BindableHolder {
        override fun bind(msg: Message) {
            b.messageText.text = msg.text
            b.messageText.isVisible = msg.text.isNotBlank()

            if (msg.imageBase64 != null) {
                val bytes = Base64.decode(msg.imageBase64, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.imagePreview.setImageBitmap(bitmap)
                b.imagePreview.isVisible = true
            } else {
                b.imagePreview.isVisible = false
            }

            b.root.setOnLongClickListener { b.root.context.copyToClipboard(msg.text); true }
        }
    }

    private class AssistantViewHolder(
        private val b: ItemMessageAssistantBinding,
        private val markwon: Markwon
    ) : RecyclerView.ViewHolder(b.root), BindableHolder {
        override fun bind(msg: Message) {
            markwon.setMarkdown(b.messageText, msg.text)
            b.messageText.setOnLongClickListener { b.messageText.context.copyToClipboard(msg.text); true }
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nachricht", text))
    Toast.makeText(this, getString(de.bedrockchat.R.string.msg_copied), Toast.LENGTH_SHORT).show()
}
