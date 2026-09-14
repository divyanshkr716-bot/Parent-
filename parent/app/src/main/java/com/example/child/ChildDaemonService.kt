package com.example.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class ChildDaemonService : Service() {

  private var daemonServer: ChildDaemonServer? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  companion object {
    const val CHANNEL_ID = "airdroid_child_daemon_channel"
    const val NOTIFICATION_ID = 1001
    @Volatile
    var isRunning = false

    fun startService(context: Context) {
      val intent = Intent(context, ChildDaemonService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, ChildDaemonService::class.java)
      context.stopService(intent)
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()

    val notification = buildForegroundNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    // Acquire locks for reliable local networking
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
      wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirDroid:ChildDaemonWakeLock")
      wakeLock?.acquire(3600 * 1000L)

      val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AirDroid:ChildDaemonWifiLock")
      wifiLock?.acquire()
    } catch (e: Exception) {}

    daemonServer = ChildDaemonServer(this, 8888)
    daemonServer?.start()
    isRunning = true
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY
  }

  override fun onDestroy() {
    isRunning = false
    daemonServer?.stop()
    daemonServer = null

    try {
      if (wakeLock?.isHeld == true) wakeLock?.release()
      if (wifiLock?.isHeld == true) wifiLock?.release()
    } catch (e: Exception) {}

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "AirDroid Child Protection Daemon",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Provides background synchronization and local P2P daemon services."
        setShowBadge(false)
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.child_daemon_notification_title))
      .setContentText(getString(R.string.child_daemon_notification_desc))
      .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }
}
