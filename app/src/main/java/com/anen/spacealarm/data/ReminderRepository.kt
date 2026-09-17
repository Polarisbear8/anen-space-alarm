package com.anen.spacealarm.data

import com.anen.spacealarm.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 数据库统一访问入口。数据库不负责地理围栏。
 */
class ReminderRepository(private val dao: ReminderDao) {

    fun observeAll(): Flow<List<Reminder>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun getActive(): List<Reminder> = dao.getActive().map { it.toModel() }

    suspend fun getById(id: Long): Reminder? = dao.getById(id)?.toModel()

    suspend fun save(reminder: Reminder): Long = dao.insert(reminder.toEntity())

    suspend fun update(reminder: Reminder) = dao.update(reminder.toEntity())

    suspend fun delete(reminder: Reminder) = dao.delete(reminder.toEntity())

    /** 原子抢占触发权。返回 true 表示本次是第一个触发者，应当执行提醒。 */
    suspend fun claimTrigger(id: Long): Boolean = dao.claimTrigger(id) == 1

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
