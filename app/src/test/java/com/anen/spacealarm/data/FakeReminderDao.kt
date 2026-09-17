package com.anen.spacealarm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 测试用 DAO。用 Mutex 模拟 SQLite 中
 * `UPDATE ... WHERE enabled = 1 AND triggered = 0` 的原子语义。
 */
class FakeReminderDao : ReminderDao {

    private val mutex = Mutex()
    private val items = linkedMapOf<Long, ReminderEntity>()
    private val state = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = state

    override suspend fun getAll(): List<ReminderEntity> =
        mutex.withLock { items.values.sortedByDescending { it.createdAt } }

    override suspend fun getActive(): List<ReminderEntity> =
        mutex.withLock { items.values.filter { it.enabled && !it.triggered } }

    override suspend fun getById(id: Long): ReminderEntity? = mutex.withLock { items[id] }

    override suspend fun insert(entity: ReminderEntity): Long = mutex.withLock {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items[id] = entity.copy(id = id)
        id
    }

    override suspend fun update(entity: ReminderEntity) {
        mutex.withLock { items[entity.id] = entity }
    }

    override suspend fun delete(entity: ReminderEntity) {
        mutex.withLock { items.remove(entity.id) }
    }

    override suspend fun claimTrigger(id: Long): Int = mutex.withLock {
        val entity = items[id] ?: return@withLock 0
        if (entity.enabled && !entity.triggered) {
            items[id] = entity.copy(triggered = true)
            1
        } else {
            0
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        mutex.withLock {
            items[id]?.let { items[id] = it.copy(enabled = enabled, triggered = false) }
        }
    }
}
