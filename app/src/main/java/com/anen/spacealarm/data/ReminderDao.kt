package com.anen.spacealarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND triggered = 0 ORDER BY createdAt DESC")
    suspend fun getActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity)

    @Delete
    suspend fun delete(entity: ReminderEntity)

    /**
     * 原子抢占触发权：只有从 enabled=1 且 triggered=0 变成 triggered=1 的那一次返回 1。
     * 用于防止同一个围栏事件被重复投递时提醒两次。
     */
    @Query("UPDATE reminders SET triggered = 1 WHERE id = :id AND enabled = 1 AND triggered = 0")
    suspend fun claimTrigger(id: Long): Int

    @Query("UPDATE reminders SET enabled = :enabled, triggered = 0 WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
