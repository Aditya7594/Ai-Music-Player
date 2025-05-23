package com.example.music.models

data class ChatMessage(
    val text: String,
    val isUserMessage: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
