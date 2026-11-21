package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    // Tasks & Smart Reminders
    @Query("SELECT * FROM tasks ORDER BY completed ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    // Daily Schedules
    @Query("SELECT * FROM schedules ORDER BY timeSlot ASC, createdAt DESC")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(item: ScheduleEntity): Long

    @Query("UPDATE schedules SET completed = :completed WHERE id = :id")
    suspend fun setScheduleCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    // Birthday Reminders
    @Query("SELECT * FROM birthdays ORDER BY month ASC, day ASC")
    fun observeBirthdays(): Flow<List<BirthdayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBirthday(birthday: BirthdayEntity): Long

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteBirthday(id: Long)

    // Conversation Logs
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    // Automation Audit Trails
    @Query("SELECT * FROM audit_trail ORDER BY timestamp DESC LIMIT 100")
    fun observeAuditTrail(): Flow<List<AuditTrailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(entry: AuditTrailEntity): Long

    @Query("DELETE FROM audit_trail")
    suspend fun clearAuditTrail()
}
