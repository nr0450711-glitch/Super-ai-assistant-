package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val dueLabel: String = "",
    val priority: String = "VIP",
    val isReminder: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timeSlot: String,
    val title: String,
    val category: String = "Focus",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "birthdays")
data class BirthdayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personName: String,
    val month: Int,
    val day: Int,
    val contactInfo: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String, // "user" or "assistant"
    val content: String,
    val language: String = "en-IN",
    val sourceLabel: String = "Local Core",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_trail")
data class AuditTrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val actionType: String,
    val summary: String,
    val status: String = "Executed",
    val timestamp: Long = System.currentTimeMillis()
)
