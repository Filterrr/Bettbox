package com.appshub.bettbox.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.FOREGROUND_SERVICE_IMMEDIATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.appshub.bettbox.GlobalState
import com.appshub.bettbox.R
import com.appshub.bettbox.models.VpnOptions

interface BaseServiceInterface {
    suspend fun start(options: VpnOptions): Int
    fun stop()
    suspend fun startForeground(title: String, content: String)
}

fun Service.createPlaceholderNotification(): Notification {
    ensureNotificationChannel()
    return createBettboxNotificationBuilder().apply {
        setContentTitle("Bettbox")
        setContentText(null)
    }.build()
}

fun Service.createBettboxNotificationBuilder(): NotificationCompat.Builder {
        val defaultComponent = ComponentName(packageName, "com.appshub.bettbox.MainActivity")
        val lightComponent = ComponentName(packageName, "com.appshub.bettbox.MainActivityLight")

        val defaultState = runCatching { packageManager.getComponentEnabledSetting(defaultComponent) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val lightState = runCatching { packageManager.getComponentEnabledSetting(lightComponent) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        val targetComponent = when {
            lightState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> lightComponent
            lightState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> defaultComponent
            defaultState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> defaultComponent
            defaultState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> lightComponent
            else -> runCatching {
                packageManager.getActivityInfo(lightComponent, 0)
                    .takeIf { it.enabled }?.let { lightComponent }
            }.getOrNull() ?: defaultComponent
        }

        android.util.Log.d("Notification", "Using ${targetComponent.className}")

        val intent = Intent().apply {
            component = targetComponent
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        return NotificationCompat.Builder(this, GlobalState.NOTIFICATION_CHANNEL).apply {
            setSmallIcon(R.drawable.ic)
            setContentTitle("Bettbox")
            setContentIntent(pendingIntent)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
            }
            setOngoing(true)
            setShowWhen(false)
            setOnlyAlertOnce(true)
        }
}

fun Service.ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    if (manager?.getNotificationChannel(GlobalState.NOTIFICATION_CHANNEL) == null) {
        manager?.createNotificationChannel(
            NotificationChannel(GlobalState.NOTIFICATION_CHANNEL, "Foreground Service", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

@SuppressLint("ForegroundServiceType")
fun Service.startForeground(notification: Notification) {
    ensureNotificationChannel()

    runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(
                    GlobalState.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(
                    GlobalState.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
                )
            }
            else -> {
                startForeground(GlobalState.NOTIFICATION_ID, notification)
            }
        }
    }.onFailure {
        android.util.Log.e("BaseServiceInterface", "startForeground failed: ${it.message}")
        runCatching {
            startForeground(GlobalState.NOTIFICATION_ID, notification)
        }.onFailure { fallback ->
            android.util.Log.e("BaseServiceInterface", "fallback startForeground failed: ${fallback.message}")
        }
    }
}
