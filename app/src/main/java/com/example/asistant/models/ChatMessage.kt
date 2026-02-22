package com.example.asistant.models

import java.util.concurrent.atomic.AtomicLong

private val _idCounter = AtomicLong(System.currentTimeMillis())

data class ChatMessage(
    val id: Long = _idCounter.incrementAndGet(),  // Atomik sayaç → duplicate key yok
    val content: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

