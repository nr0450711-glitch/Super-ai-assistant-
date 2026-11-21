package com.example.data.local

import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val dao: JarvisDao) {
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val schedules: Flow<List<ScheduleEntity>> = dao.observeSchedules()
    val birthdays: Flow<List<BirthdayEntity>> = dao.observeBirthdays()
    val messages: Flow<List<ChatMessageEntity>> = dao.observeMessages()
    val auditTrail: Flow<List<AuditTrailEntity>> = dao.observeAuditTrail()

    suspend fun addTask(
        title: String,
        notes: String = "",
        dueLabel: String = "",
        priority: String = "VIP",
        isReminder: Boolean = false
    ) {
        dao.insertTask(
            TaskEntity(
                title = title.trim(),
                notes = notes.trim(),
                dueLabel = dueLabel.trim(),
                priority = priority,
                isReminder = isReminder
            )
        )
    }

    suspend fun setTaskCompleted(id: Long, completed: Boolean) = dao.setTaskCompleted(id, completed)

    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    suspend fun addSchedule(timeSlot: String, title: String, category: String = "Focus") {
        dao.insertSchedule(
            ScheduleEntity(
                timeSlot = timeSlot.trim(),
                title = title.trim(),
                category = category.trim()
            )
        )
    }

    suspend fun setScheduleCompleted(id: Long, completed: Boolean) = dao.setScheduleCompleted(id, completed)

    suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    suspend fun addBirthday(personName: String, month: Int, day: Int, contactInfo: String = "", note: String = "") {
        dao.insertBirthday(
            BirthdayEntity(
                personName = personName.trim(),
                month = month.coerceIn(1, 12),
                day = day.coerceIn(1, 31),
                contactInfo = contactInfo.trim(),
                note = note.trim()
            )
        )
    }

    suspend fun deleteBirthday(id: Long) = dao.deleteBirthday(id)

    suspend fun appendMessage(role: String, content: String, language: String, sourceLabel: String) {
        dao.insertMessage(
            ChatMessageEntity(
                role = role,
                content = content,
                language = language,
                sourceLabel = sourceLabel
            )
        )
    }

    suspend fun clearConversation() = dao.clearMessages()

    suspend fun logAudit(actionType: String, summary: String, status: String = "Executed") {
        dao.insertAudit(
            AuditTrailEntity(
                actionType = actionType,
                summary = summary,
                status = status
            )
        )
    }

    suspend fun clearAuditTrail() = dao.clearAuditTrail()
}
