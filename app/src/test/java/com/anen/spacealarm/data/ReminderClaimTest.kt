package com.anen.spacealarm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重复触发保护：同一个围栏事件被重复投递时，只能有一次提醒。
 */
class ReminderClaimTest {

    private fun entity(enabled: Boolean = true, triggered: Boolean = false) = ReminderEntity(
        id = 0L,
        placeName = "南方医科大学珠江医院",
        address = "工业大道中253号",
        latitude = 23.079347,
        longitude = 113.267724,
        radiusMeters = 500f,
        message = "快到了",
        alertMode = "NOTIFICATION",
        repeatEnabled = false,
        enabled = enabled,
        triggered = triggered,
        createdAt = 1L
    )

    @Test
    fun `only one concurrent claim wins`() = runTest {
        val dao = FakeReminderDao()
        val id = dao.insert(entity())
        val repository = ReminderRepository(dao)

        val results = (1..32)
            .map { async(Dispatchers.Default) { repository.claimTrigger(id) } }
            .awaitAll()

        assertEquals(1, results.count { it })
        assertTrue(repository.getById(id)!!.triggered)
    }

    @Test
    fun `claim fails when disabled or already triggered`() = runTest {
        val dao = FakeReminderDao()
        val disabledId = dao.insert(entity(enabled = false))
        val triggeredId = dao.insert(entity(triggered = true))
        val repository = ReminderRepository(dao)

        assertFalse(repository.claimTrigger(disabledId))
        assertFalse(repository.claimTrigger(triggeredId))
    }

    @Test
    fun `rearm after trigger allows next claim`() = runTest {
        val dao = FakeReminderDao()
        val id = dao.insert(entity())
        val repository = ReminderRepository(dao)

        assertTrue(repository.claimTrigger(id))
        assertFalse(repository.claimTrigger(id))

        repository.setEnabled(id, true)
        assertTrue("重新启用后应可以再次触发", repository.claimTrigger(id))
    }
}
