package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StealthManager(private val context: Context) {

    companion object {
        private const val TAG = "StealthManager"
        private const val PREFS_NAME = "airdroid_stealth_prefs"

        private const val KEY_SETUP_COMPLETED = "key_setup_completed"
        private const val KEY_AUTO_CLICKER_ACTIVE = "key_auto_clicker_active"
        private const val KEY_CAMOUFLAGE_ENABLED = "key_camouflage_enabled"
        private const val KEY_CAMOUFLAGE_PIN = "key_camouflage_pin"
        private const val KEY_ICON_HIDDEN = "key_icon_hidden"
        private const val KEY_STEALTH_NOTIFICATION = "key_stealth_notification"
        private const val KEY_BLOCKED_PACKAGES = "key_blocked_packages"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Flow states for Compose reactivity
    private val _isSetupCompleted = MutableStateFlow(prefs.getBoolean(KEY_SETUP_COMPLETED, false))
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    private val _isAutoClickerActive = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CLICKER_ACTIVE, true))
    val isAutoClickerActive: StateFlow<Boolean> = _isAutoClickerActive.asStateFlow()

    private val _isCamouflageEnabled = MutableStateFlow(prefs.getBoolean(KEY_CAMOUFLAGE_ENABLED, false))
    val isCamouflageEnabled: StateFlow<Boolean> = _isCamouflageEnabled.asStateFlow()

    private val _isAppIconHidden = MutableStateFlow(prefs.getBoolean(KEY_ICON_HIDDEN, false))
    val isAppIconHidden: StateFlow<Boolean> = _isAppIconHidden.asStateFlow()

    private val _isStealthNotification = MutableStateFlow(prefs.getBoolean(KEY_STEALTH_NOTIFICATION, true))
    val isStealthNotification: StateFlow<Boolean> = _isStealthNotification.asStateFlow()

    private val _blockedPackages = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    )
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    // Transient session unlock for camouflage calculator
    private val _isCamouflageTemporarilyUnlocked = MutableStateFlow(false)
    val isCamouflageTemporarilyUnlocked: StateFlow<Boolean> = _isCamouflageTemporarilyUnlocked.asStateFlow()

    fun getCamouflagePin(): String = prefs.getString(KEY_CAMOUFLAGE_PIN, "1234") ?: "1234"

    fun setCamouflagePin(pin: String) {
        prefs.edit().putString(KEY_CAMOUFLAGE_PIN, pin).apply()
    }

    fun verifyCamouflagePin(entered: String): Boolean {
        val expected = getCamouflagePin()
        val match = entered.trim() == expected.trim()
        if (match) {
            _isCamouflageTemporarilyUnlocked.value = true
        }
        return match
    }

    fun lockCamouflage() {
        _isCamouflageTemporarilyUnlocked.value = false
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
        _isSetupCompleted.value = completed
    }

    fun setAutoClickerActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLICKER_ACTIVE, active).apply()
        _isAutoClickerActive.value = active
    }

    fun setCamouflageEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAMOUFLAGE_ENABLED, enabled).apply()
        _isCamouflageEnabled.value = enabled
        if (!enabled) {
            _isCamouflageTemporarilyUnlocked.value = true
        } else {
            _isCamouflageTemporarilyUnlocked.value = false
        }
    }

    fun setStealthNotification(stealth: Boolean) {
        prefs.edit().putBoolean(KEY_STEALTH_NOTIFICATION, stealth).apply()
        _isStealthNotification.value = stealth
    }

    fun setAppIconHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_ICON_HIDDEN, hidden).apply()
        _isAppIconHidden.value = hidden

        try {
            val componentName = ComponentName(context, MainActivity::class.java)
            val newState = if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            context.packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "App icon visibility updated: hidden=$hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle icon visibility", e)
        }
    }

    fun isPackageBlocked(packageName: String): Boolean {
        return _blockedPackages.value.contains(packageName)
    }

    fun addBlockedPackage(packageName: String) {
        val updated = _blockedPackages.value.toMutableSet().apply { add(packageName) }
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
        _blockedPackages.value = updated
    }

    fun removeBlockedPackage(packageName: String) {
        val updated = _blockedPackages.value.toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
        _blockedPackages.value = updated
    }
}
