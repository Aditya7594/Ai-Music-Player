package com.example.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music.models.ChatMessage

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message)
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userMessageCard: CardView = itemView.findViewById(R.id.userMessageCard)
        private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val aiMessageCard: CardView = itemView.findViewById(R.id.aiMessageCard)
        private val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)

        fun bind(message: ChatMessage) {
            if (message.isUserMessage) {
                userMessageCard.visibility = View.VISIBLE
                aiMessageCard.visibility = View.GONE
                userMessageText.text = message.text
            } else {
                userMessageCard.visibility = View.GONE
                aiMessageCard.visibility = View.VISIBLE
                aiMessageText.text = message.text
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
