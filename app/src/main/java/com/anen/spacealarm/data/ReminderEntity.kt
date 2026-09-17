package com.anen.spacealarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Reminder

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val placeName: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val message: String,
    val alertMode: String,
    val repeatEnabled: Boolean,
    val enabled: Boolean,
    val triggered: Boolean,
    val createdAt: Long
)

fun ReminderEntity.toModel(): Reminder = Reminder(
    id = id,
    placeName = placeName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    message = message,
    alertMode = runCatching { AlertMode.valueOf(alertMode) }.getOrDefault(AlertMode.NOTIFICATION),
    repeat = repeatEnabled,
    enabled = enabled,
    triggered = triggered,
    createdAt = createdAt
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    placeName = placeName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    message = message,
    alertMode = alertMode.name,
    repeatEnabled = repeat,
    enabled = enabled,
    triggered = triggered,
    createdAt = createdAt
)
