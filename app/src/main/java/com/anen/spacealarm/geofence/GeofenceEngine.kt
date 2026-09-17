package com.anen.spacealarm.geofence

import com.anen.spacealarm.model.Reminder

/**
 * 地理围栏抽象。业务层只依赖这个接口，不关心具体实现。
 */
interface GeofenceEngine {

    /**
     * 当前设备/系统是否具备使用该围栏实现的条件
     * （例如 Google Play services 是否可用、厂商位置服务是否就绪）。
     */
    fun isAvailable(): Boolean

    suspend fun add(reminder: Reminder)
    suspend fun remove(reminderId: Long)
    suspend fun update(reminder: Reminder)
    suspend fun removeAll()

    companion object {
        /**
         * 系统围栏事件广播的 action。由各实现注册 PendingIntent、由
         * GeofenceBroadcastReceiver 接收，双方都只依赖这个公共契约，
         * 不依赖具体实现类。
         */
        const val ACTION_GEOFENCE = "com.anen.spacealarm.action.GEOFENCE"
    }
}
