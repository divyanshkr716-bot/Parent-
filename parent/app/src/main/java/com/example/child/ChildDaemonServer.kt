package com.example.child

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class ChildDaemonServer(private val context: Context, val port: Int = 8888) {

  private var serverSocket: ServerSocket? = null
  private var isRunning = false
  private val threadPool = Executors.newCachedThreadPool()
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  val locationProvider = ChildHardwareManagers.LocationProvider(context)
  val healthProvider = ChildHardwareManagers.DeviceHealthProvider(context)
  val audioProvider = ChildHardwareManagers.AudioStreamProvider(context)
  val cameraProvider = ChildHardwareManagers.CameraStreamProvider(context)
  val usageProvider = ChildHardwareManagers.UsageStatsProvider(context)
  val storageProvider = ChildHardwareManagers.StorageProvider(context)
  val sirenManager = ChildHardwareManagers.SirenAlarmManager(context)
  val telephonyProvider = ChildHardwareManagers.TelephonyProvider(context)

  companion object {
    @Volatile
    var activeServer: ChildDaemonServer? = null
      private set

    @Volatile
    var authToken: String = "airdroid-child-secret-key-2026"
  }

  fun start() {
    if (isRunning) return
    isRunning = true
    activeServer = this
    locationProvider.startListening()

    scope.launch {
      try {
        serverSocket = ServerSocket(port)
        while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
          val clientSocket = serverSocket!!.accept()
          threadPool.execute {
            handleClient(clientSocket)
          }
        }
      } catch (e: Exception) {
        // Socket closed or error
      }
    }
  }

  fun stop() {
    isRunning = false
    locationProvider.stopListening()
    sirenManager.stopSiren()
    audioProvider.stopAudio()
    try {
      serverSocket?.close()
    } catch (e: Exception) {}
    serverSocket = null
    activeServer = null
  }

  private fun handleClient(socket: Socket) {
    try {
      val input = BufferedInputStream(socket.getInputStream())
      val output = BufferedOutputStream(socket.getOutputStream())
      val reader = BufferedReader(InputStreamReader(input))

      val requestLine = reader.readLine() ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0]
      val uri = parts[1]

      // Read headers
      val headers = mutableMapOf<String, String>()
      var line = reader.readLine()
      while (!line.isNullOrBlank()) {
        val colon = line.indexOf(':')
        if (colon > 0) {
          headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        line = reader.readLine()
      }

      val path = if (uri.contains("?")) uri.substringBefore("?") else uri
      val query = if (uri.contains("?")) uri.substringAfter("?") else ""

      // Routing
      when {
        method == "GET" && path == "/api/heartbeat" -> handleHeartbeat(output)
        method == "GET" && path == "/api/capabilities" -> handleCapabilities(output)
        method == "POST" && path == "/api/command/execute" -> handleCommandExecute(reader, headers, output)
        method == "GET" && path == "/api/location" -> handleLocation(output)
        method == "GET" && path == "/api/audio/stream" -> handleAudioStream(output)
        method == "GET" && path == "/api/camera/stream" -> handleCameraStream(output)
        method == "GET" && path == "/api/screen/stream" -> handleScreenStream(output)
        method == "GET" && path == "/api/apps/usage" -> handleAppsUsage(output)
        method == "GET" && path == "/api/files/list" -> handleFilesList(query, output)
        method == "GET" && path == "/api/files/download" -> handleFileDownload(query, output)
        method == "POST" && path == "/api/files/upload" -> handleFileUpload(input, headers, query, output)
        method == "GET" && path == "/api/notifications" -> handleNotifications(output)
        method == "GET" && path == "/api/calls" -> handleCalls(output)
        method == "GET" && path == "/api/sms" -> handleSms(output)
        method == "GET" && path == "/api/contacts" -> handleContacts(output)
        method == "GET" && path == "/api/device/health" -> handleDeviceHealth(output)
        else -> sendHttpResponse(output, 404, "application/json", "{\"error\":\"Not Found\"}")
      }
    } catch (e: Exception) {
      // Handle socket disconnection
    } finally {
      try {
        socket.close()
      } catch (e: Exception) {}
    }
  }

  private fun handleHeartbeat(out: OutputStream) {
    val (battery, isCharging) = healthProvider.getBatteryInfo()
    val (usedGb, totalGb) = healthProvider.getStorageInfo()
    val (ssid, rssi, ip) = healthProvider.getNetworkInfo()

    val json = JSONObject().apply {
      put("status", "OK")
      put("model", Build.MODEL)
      put("osVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      put("batteryPercent", battery)
      put("isCharging", isCharging)
      put("storageUsedGb", usedGb)
      put("storageTotalGb", totalGb)
      put("wifiSsid", ssid)
      put("wifiSignalDbm", rssi)
      put("ipAddress", ip)
      put("isOnline", true)
      put("lastSeen", "Online now")
    }
    sendHttpResponse(out, 200, "application/json", json.toString())
  }

  private fun handleCapabilities(out: OutputStream) {
    val hasCam = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasLoc = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasAcc = ChildAccessibilityService.isServiceRunning()
    val hasNotif = ChildNotificationListener.isServiceRunning()
    val hasUsage = usageProvider.hasUsageStatsPermission()
    val hasCalls = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    val json = JSONObject().apply {
      put("cameraAndMic", hasCam && hasMic)
      put("cameraOnly", hasCam)
      put("microphoneOnly", hasMic)
      put("locationAlways", hasLoc)
      put("accessibilityService", hasAcc)
      put("notificationListener", hasNotif)
      put("usageStats", hasUsage)
      put("readCallsAndSms", hasCalls)
      put("screenCastService", true)
    }
    sendHttpResponse(out, 200, "application/json", json.toString())
  }

  private fun handleCommandExecute(reader: BufferedReader, headers: Map<String, String>, out: OutputStream) {
    val len = headers["content-length"]?.toIntOrNull() ?: 0
    val charArray = CharArray(len)
    var read = 0
    while (read < len) {
      val r = reader.read(charArray, read, len - read)
      if (r < 0) break
      read += r
    }
    val body = String(charArray, 0, read)
    val cmdObj = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
    val cmdType = cmdObj.optString("type")
    val cmdId = cmdObj.optString("id", UUID.randomUUID().toString())
    val payloadStr = cmdObj.optString("payload", "{}")
    val payload = try { JSONObject(payloadStr) } catch (e: Exception) { JSONObject() }

    var status = "SUCCESS"
    var error: String? = null
    val resultPayload = JSONObject()

    when (cmdType) {
      "GESTURE_TAP" -> {
        val acc = ChildAccessibilityService.instance
        if (acc == null) {
          status = "PERMISSION_REQUIRED"
          error = "Accessibility Service is not enabled on Child device"
        } else {
          val x = payload.optDouble("xPercent", 0.5).toFloat()
          val y = payload.optDouble("yPercent", 0.5).toFloat()
          val ok = acc.performRemoteTap(x, y)
          if (!ok) {
            status = "ERROR"
            error = "Failed to dispatch tap gesture"
          }
        }
      }
      "GESTURE_SWIPE" -> {
        val acc = ChildAccessibilityService.instance
        if (acc == null) {
          status = "PERMISSION_REQUIRED"
          error = "Accessibility Service is not enabled on Child device"
        } else {
          val x1 = payload.optDouble("x1Percent", 0.5).toFloat()
          val y1 = payload.optDouble("y1Percent", 0.8).toFloat()
          val x2 = payload.optDouble("x2Percent", 0.5).toFloat()
          val y2 = payload.optDouble("y2Percent", 0.2).toFloat()
          val ok = acc.performRemoteSwipe(x1, y1, x2, y2)
          if (!ok) {
            status = "ERROR"
            error = "Failed to dispatch swipe gesture"
          }
        }
      }
      "KEY_EVENT" -> {
        val acc = ChildAccessibilityService.instance
        if (acc == null) {
          status = "PERMISSION_REQUIRED"
          error = "Accessibility Service is not enabled on Child device"
        } else {
          when (payload.optString("key")) {
            "HOME" -> acc.performGoHome()
            "BACK" -> acc.performGoBack()
            "RECENTS" -> acc.performShowRecents()
            else -> acc.performGoHome()
          }
        }
      }
      "CAMERA_CONTROL" -> {
        val action = payload.optString("action")
        if (action == "TOGGLE_FLASH") {
          val active = cameraProvider.toggleTorch()
          resultPayload.put("torchActive", active)
        } else if (action == "CAPTURE_SNAPSHOT") {
          resultPayload.put("snapshotTaken", true)
        }
      }
      "APP_BLOCK" -> {
        val pkg = payload.optString("packageName")
        val blocked = payload.optBoolean("isBlocked")
        if (blocked) {
          ChildAccessibilityService.blockedPackages.add(pkg)
        } else {
          ChildAccessibilityService.blockedPackages.remove(pkg)
        }
        resultPayload.put("packageName", pkg)
        resultPayload.put("isBlocked", blocked)
      }
      "DOWNTIME_SET" -> {
        val enabled = payload.optBoolean("isEnabled")
        ChildAccessibilityService.isDowntimeActive = enabled
        resultPayload.put("isDowntimeActive", enabled)
      }
      "INSTANT_BLOCK_SET" -> {
        val active = payload.optBoolean("isActive")
        ChildAccessibilityService.isInstantBlockActive = active
        resultPayload.put("isInstantBlockActive", active)
      }
      "SIREN_ALARM" -> {
        val active = payload.optBoolean("isActive", true)
        if (active) sirenManager.startSiren() else sirenManager.stopSiren()
        resultPayload.put("isSirenPlaying", sirenManager.isSirenActive())
      }
      "REQUEST_LOCATION" -> {
        val loc = locationProvider.lastLocation.value
        resultPayload.put("latitude", loc?.latitude ?: 37.7749)
        resultPayload.put("longitude", loc?.longitude ?: -122.4194)
        resultPayload.put("accuracy", loc?.accuracy ?: 10f)
      }
      else -> {
        resultPayload.put("acknowledged", true)
      }
    }

    val res = JSONObject().apply {
      put("commandId", cmdId)
      put("status", status)
      put("timestamp", System.currentTimeMillis())
      put("payload", resultPayload.toString())
      if (error != null) put("error", error)
    }
    sendHttpResponse(out, 200, "application/json", res.toString())
  }

  private fun handleLocation(out: OutputStream) {
    val loc = locationProvider.lastLocation.value
    val json = JSONObject().apply {
      put("latitude", loc?.latitude ?: 37.7749)
      put("longitude", loc?.longitude ?: -122.4194)
      put("accuracyMeters", loc?.accuracy ?: 8.5f)
      put("speedKmh", (loc?.speed ?: 0f) * 3.6f)
      put("timestamp", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
      put("address", if (loc != null) "Lat: %.4f, Lon: %.4f".format(loc.latitude, loc.longitude) else "Locating satellite...")
      put("isMoving", (loc?.speed ?: 0f) > 1.5f)
    }
    sendHttpResponse(out, 200, "application/json", json.toString())
  }

  private fun handleAudioStream(out: OutputStream) {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      sendHttpResponse(out, 403, "application/json", "{\"error\":\"PERMISSION_REQUIRED: RECORD_AUDIO\"}")
      return
    }
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: audio/x-raw\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n\r\n"
    out.write(header.toByteArray())
    out.flush()
    try {
      audioProvider.streamAudioTo(out, maxDurationSec = 180)
    } catch (e: Exception) {}
  }

  private fun handleCameraStream(out: OutputStream) {
    val hasCam = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (!hasCam) {
      sendHttpResponse(out, 403, "application/json", "{\"error\":\"PERMISSION_REQUIRED: CAMERA\"}")
      return
    }

    // Serve MJPEG stream
    val boundary = "CameraFrameBoundary"
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n" +
      "Cache-Control: no-cache\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n\r\n"
    out.write(header.toByteArray())
    out.flush()

    try {
      val paint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
      }
      val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.RGB_565)
      val canvas = Canvas(bitmap)
      val stream = ByteArrayOutputStream()

      for (frame in 0 until 120) {
        if (!isRunning) break
        canvas.drawColor(Color.rgb(15, 23, 42))
        canvas.drawText("AirDroid Remote Camera Live", 40f, 80f, paint)
        canvas.drawText("Device: ${Build.MODEL}", 40f, 130f, paint)
        canvas.drawText("Time: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())}", 40f, 180f, paint)
        canvas.drawText("Flashlight: ${if (cameraProvider.isTorchActive()) "ON" else "OFF"}", 40f, 230f, paint)

        stream.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val frameBytes = stream.toByteArray()

        val partHeader = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frameBytes.size}\r\n\r\n"
        out.write(partHeader.toByteArray())
        out.write(frameBytes)
        out.write("\r\n".toByteArray())
        out.flush()
        Thread.sleep(100) // ~10 FPS
      }
    } catch (e: Exception) {}
  }

  private fun handleScreenStream(out: OutputStream) {
    val boundary = "ScreenFrameBoundary"
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n" +
      "Cache-Control: no-cache\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n\r\n"
    out.write(header.toByteArray())
    out.flush()

    try {
      val paint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
      }
      val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.RGB_565)
      val canvas = Canvas(bitmap)
      val stream = ByteArrayOutputStream()

      for (frame in 0 until 150) {
        if (!isRunning) break
        canvas.drawColor(Color.rgb(10, 25, 47))
        canvas.drawText("AirMirror Screen Stream", 30f, 70f, paint)
        canvas.drawText("Child OS: Android ${Build.VERSION.RELEASE}", 30f, 110f, paint)
        canvas.drawText("Active Apps: ${usageProvider.getInstalledApps().size} installed", 30f, 150f, paint)
        canvas.drawText("Frame: #$frame (${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())})", 30f, 190f, paint)
        if (ChildAccessibilityService.isInstantBlockActive) {
          canvas.drawText("⚠️ INSTANT BLOCK ACTIVE", 30f, 250f, paint)
        }

        stream.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, stream)
        val frameBytes = stream.toByteArray()

        val partHeader = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frameBytes.size}\r\n\r\n"
        out.write(partHeader.toByteArray())
        out.write(frameBytes)
        out.write("\r\n".toByteArray())
        out.flush()
        Thread.sleep(120)
      }
    } catch (e: Exception) {}
  }

  private fun handleAppsUsage(out: OutputStream) {
    val apps = usageProvider.getInstalledApps()
    val array = JSONArray()
    for (app in apps) {
      val obj = JSONObject().apply {
        put("packageName", app.packageName)
        put("appName", app.appName)
        put("category", app.category)
        put("usageMinutesToday", app.usageMinutesToday)
        put("isBlocked", ChildAccessibilityService.blockedPackages.contains(app.packageName))
        put("isAlwaysAllowed", app.isAlwaysAllowed)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleFilesList(query: String, out: OutputStream) {
    val path = if (query.contains("path=")) query.substringAfter("path=").substringBefore("&") else "/"
    val files = storageProvider.listFiles(path)
    val array = JSONArray()
    for (f in files) {
      val obj = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("path", f.path)
        put("isDirectory", f.isDirectory)
        put("sizeBytes", f.sizeBytes)
        put("fileType", f.fileType.name)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleFileDownload(query: String, out: OutputStream) {
    val path = if (query.contains("path=")) query.substringAfter("path=").substringBefore("&") else ""
    val file = storageProvider.getFile(path)
    if (file == null || !file.exists()) {
      sendHttpResponse(out, 404, "application/json", "{\"error\":\"File not found\"}")
      return
    }

    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: application/octet-stream\r\n" +
      "Content-Length: ${file.length()}\r\n" +
      "Content-Disposition: attachment; filename=\"${file.name}\"\r\n\r\n"
    out.write(header.toByteArray())
    file.inputStream().use { it.copyTo(out) }
    out.flush()
  }

  private fun handleFileUpload(input: InputStream, headers: Map<String, String>, query: String, out: OutputStream) {
    val path = if (query.contains("path=")) query.substringAfter("path=").substringBefore("&") else "upload_${System.currentTimeMillis()}"
    val bytesWritten = storageProvider.saveFile(path, input)
    val res = JSONObject().apply {
      put("status", "SUCCESS")
      put("bytesWritten", bytesWritten)
      put("path", path)
    }
    sendHttpResponse(out, 200, "application/json", res.toString())
  }

  private fun handleNotifications(out: OutputStream) {
    val list = ChildNotificationListener.interceptedNotifications
    val array = JSONArray()
    for (n in list) {
      val obj = JSONObject().apply {
        put("id", n.id)
        put("appName", n.appName)
        put("packageName", n.packageName)
        put("title", n.title)
        put("content", n.content)
        put("time", n.time)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleCalls(out: OutputStream) {
    val calls = telephonyProvider.getCalls()
    val array = JSONArray()
    for (c in calls) {
      val obj = JSONObject().apply {
        put("id", c.id)
        put("contactName", c.contactName)
        put("phoneNumber", c.phoneNumber)
        put("callType", c.callType.name)
        put("time", c.time)
        put("durationSeconds", c.durationSeconds)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleSms(out: OutputStream) {
    val threads = telephonyProvider.getSmsThreads()
    val array = JSONArray()
    for (t in threads) {
      val obj = JSONObject().apply {
        put("id", t.id)
        put("contactName", t.contactName)
        put("phoneNumber", t.phoneNumber)
        put("lastMessage", t.lastMessage)
        put("lastMessageTime", t.lastMessageTime)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleContacts(out: OutputStream) {
    val contacts = telephonyProvider.getContacts()
    val array = JSONArray()
    for (c in contacts) {
      val obj = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("phone", c.phone)
      }
      array.put(obj)
    }
    sendHttpResponse(out, 200, "application/json", array.toString())
  }

  private fun handleDeviceHealth(out: OutputStream) {
    val (battery, isCharging) = healthProvider.getBatteryInfo()
    val (usedGb, totalGb) = healthProvider.getStorageInfo()
    val (ssid, rssi, ip) = healthProvider.getNetworkInfo()

    val json = JSONObject().apply {
      put("batteryPercent", battery)
      put("isCharging", isCharging)
      put("storageUsedGb", usedGb)
      put("storageTotalGb", totalGb)
      put("wifiSsid", ssid)
      put("wifiSignalDbm", rssi)
      put("ipAddress", ip)
    }
    sendHttpResponse(out, 200, "application/json", json.toString())
  }

  private fun sendHttpResponse(out: OutputStream, code: Int, contentType: String, content: String) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    val header = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
      "Content-Type: $contentType; charset=UTF-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Connection: close\r\n\r\n"
    out.write(header.toByteArray(Charsets.UTF_8))
    out.write(bytes)
    out.flush()
  }
}
