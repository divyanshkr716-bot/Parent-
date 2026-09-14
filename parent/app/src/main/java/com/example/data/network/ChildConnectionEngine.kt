package com.example.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.AppLogger
import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Connection Engine responsible for end-to-end authenticated communication
 * with the Child App's EmbeddedDaemonServer and encrypted cloud relay tunnels.
 * Adheres strictly to the Command/Result protocol without simulation or synthetic data generation.
 */
class ChildConnectionEngine(
  private val scope: CoroutineScope
) {
  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .writeTimeout(3, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  private val _connectionStatus = MutableStateFlow(ConnectionStateStatus.DISCONNECTED)
  val connectionStatus: StateFlow<ConnectionStateStatus> = _connectionStatus.asStateFlow()

  private val _lastAckMessage = MutableStateFlow("Ready")
  val lastAckMessage: StateFlow<String> = _lastAckMessage.asStateFlow()

  private val _audioVolumeDb = MutableStateFlow(0f)
  val audioVolumeDb: StateFlow<Float> = _audioVolumeDb.asStateFlow()

  private val _lastMeasuredLatencyMs = MutableStateFlow(0)
  val lastMeasuredLatencyMs: StateFlow<Int> = _lastMeasuredLatencyMs.asStateFlow()

  private val _measuredFps = MutableStateFlow(0)
  val measuredFps: StateFlow<Int> = _measuredFps.asStateFlow()

  private var heartbeatJob: Job? = null
  private var audioPlaybackJob: Job? = null
  private var audioTrack: AudioTrack? = null
  private var isAudioStreaming = false

  // Security: Replay & Rate limiting
  private val commandTimestamps = ConcurrentHashMap<String, Long>()
  private val authTokens = ConcurrentHashMap<String, String>()
  private val commandRateCounter = AtomicInteger(0)
  private var lastRateResetTime = System.currentTimeMillis()

  fun startMonitoringDevice(device: ChildDevice) {
    heartbeatJob?.cancel()
    AppLogger.log(AppLogger.Category.CONNECTION, "Starting connection heartbeat monitor for device ${device.id} (${device.name})")
    heartbeatJob = scope.launch(Dispatchers.IO) {
      var missedBeats = 0
      while (isActive) {
        val startNanos = System.nanoTime()
        val isReachable = performHeartbeat(device)
        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000).toInt().coerceAtLeast(1)

        if (isReachable) {
          missedBeats = 0
          _connectionStatus.value = ConnectionStateStatus.CONNECTED
          _lastMeasuredLatencyMs.value = elapsedMs
        } else {
          missedBeats++
          AppLogger.log(AppLogger.Category.CONNECTION, "Missed heartbeat count: $missedBeats for device ${device.id}")
          if (missedBeats in 1..2) {
            _connectionStatus.value = ConnectionStateStatus.RECONNECTING
          } else if (missedBeats >= 3) {
            _connectionStatus.value = ConnectionStateStatus.DISCONNECTED
          }
        }
        delay(4000)
      }
    }
  }

  fun stopMonitoring() {
    heartbeatJob?.cancel()
    heartbeatJob = null
    AppLogger.log(AppLogger.Category.CONNECTION, "Stopped monitoring child device.")
  }

  private suspend fun performHeartbeat(device: ChildDevice): Boolean = withContext(Dispatchers.IO) {
    try {
      val url = getBaseUrl(device) + "/api/heartbeat"
      val request = Request.Builder()
        .url(url)
        .header("X-AirDroid-Auth", "Bearer ${authTokens[device.id] ?: device.authToken}")
        .get()
        .build()

      val response = try {
        okHttpClient.newCall(request).execute()
      } catch (e: Exception) {
        null
      }

      if (response != null && response.isSuccessful) {
        response.close()
        true
      } else {
        // In local mode if server is active, returns true; otherwise false
        response?.close()
        false
      }
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Central Command & Control Protocol:
   * COMMAND { id, type, childDeviceId, timestamp, payload }
   * RESULT { commandId, status, timestamp, payload, error }
   */
  suspend fun executeCommand(command: ChildCommand, device: ChildDevice): CommandResult = withContext(Dispatchers.IO) {
    // Rate limit check: max 15 commands per second
    val now = System.currentTimeMillis()
    if (now - lastRateResetTime > 1000L) {
      commandRateCounter.set(0)
      lastRateResetTime = now
    }
    if (commandRateCounter.incrementAndGet() > 15) {
      AppLogger.log(AppLogger.Category.ERROR, "Command rate limit exceeded for device ${command.childDeviceId}")
      return@withContext CommandResult(
        commandId = command.id,
        status = "RATE_LIMITED",
        timestamp = now,
        error = "Too many commands in short succession."
      )
    }

    // Replay attack prevention: must be within 60 seconds
    if (Math.abs(now - command.timestamp) > 60_000L) {
      AppLogger.log(AppLogger.Category.ERROR, "Rejected stale command replay: ${command.id}")
      return@withContext CommandResult(
        commandId = command.id,
        status = "REPLAY_REJECTED",
        timestamp = now,
        error = "Command timestamp out of acceptable window."
      )
    }

    AppLogger.log(AppLogger.Category.COMMAND, "Executing command ${command.type} (ID: ${command.id}) on ${device.name}")

    val jsonRequest = JSONObject().apply {
      put("id", command.id)
      put("type", command.type)
      put("childDeviceId", command.childDeviceId)
      put("timestamp", command.timestamp)
      put("payload", command.payload)
    }

    try {
      val url = getBaseUrl(device) + "/api/command/execute"
      val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
      val request = Request.Builder()
        .url(url)
        .header("X-AirDroid-Auth", "Bearer ${authTokens[device.id] ?: device.authToken}")
        .post(body)
        .build()

      val response = try {
        okHttpClient.newCall(request).execute()
      } catch (e: Exception) {
        null
      }

      val result = if (response != null && response.isSuccessful) {
        val bodyStr = response.body?.string() ?: "{}"
        response.close()
        val resJson = try { JSONObject(bodyStr) } catch (e: Exception) { JSONObject() }
        CommandResult(
          commandId = command.id,
          status = resJson.optString("status", "SUCCESS"),
          timestamp = resJson.optLong("timestamp", System.currentTimeMillis()),
          payload = resJson.optString("payload", "{}")
        )
      } else {
        response?.close()
        // If socket is disconnected, return offline/timeout status without fake data
        CommandResult(
          commandId = command.id,
          status = if (_connectionStatus.value == ConnectionStateStatus.CONNECTED) "ERROR" else "QUEUED_OFFLINE",
          timestamp = System.currentTimeMillis(),
          error = "Child device did not respond."
        )
      }

      AppLogger.log(AppLogger.Category.COMMAND_RESULT, "Command ${command.type} -> ${result.status}")
      withContext(Dispatchers.Main) {
        _lastAckMessage.value = "${command.type}: ${result.status}"
      }
      result
    } catch (e: Exception) {
      AppLogger.log(AppLogger.Category.ERROR, "Command execution error for ${command.type}", e)
      CommandResult(
        commandId = command.id,
        status = "ERROR",
        timestamp = System.currentTimeMillis(),
        error = e.localizedMessage ?: "Network I/O Error"
      )
    }
  }

  suspend fun sendTapCommand(device: ChildDevice, xRatio: Float, yRatio: Float, screenW: Int = 1080, screenH: Int = 2400): Boolean {
    val absX = (xRatio * screenW).toInt()
    val absY = (yRatio * screenH).toInt()
    val payload = JSONObject().apply {
      put("x", absX)
      put("y", absY)
    }.toString()

    val cmd = ChildCommand(
      id = "tap-${UUID.randomUUID().toString().take(8)}",
      type = "GESTURE_TAP",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendSwipeCommand(device: ChildDevice, direction: String, screenW: Int = 1080, screenH: Int = 2400): Boolean {
    val payload = JSONObject().apply {
      put("direction", direction)
      put("durationMs", 250)
    }.toString()

    val cmd = ChildCommand(
      id = "swp-${UUID.randomUUID().toString().take(8)}",
      type = "GESTURE_SWIPE",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendKeyCommand(device: ChildDevice, keyCode: String): Boolean {
    val payload = JSONObject().apply {
      put("keyCode", keyCode)
    }.toString()

    val cmd = ChildCommand(
      id = "key-${UUID.randomUUID().toString().take(8)}",
      type = "KEY_EVENT",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendTextInput(device: ChildDevice, text: String): Boolean {
    val payload = JSONObject().apply {
      put("text", text)
    }.toString()

    val cmd = ChildCommand(
      id = "txt-${UUID.randomUUID().toString().take(8)}",
      type = "TEXT_INPUT",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendSms(device: ChildDevice, phoneNumber: String, text: String): Boolean {
    val payload = JSONObject().apply { put("phoneNumber", phoneNumber); put("text", text) }.toString()
    val cmd = ChildCommand("sms-${UUID.randomUUID().toString().take(8)}", "SEND_SMS", device.id, System.currentTimeMillis(), payload)
    return executeCommand(cmd, device).status == "SUCCESS"
  }

  suspend fun sendCameraControl(device: ChildDevice, action: String): Boolean {
    val payload = JSONObject().apply {
      put("action", action)
    }.toString()

    val cmd = ChildCommand(
      id = "cam-${UUID.randomUUID().toString().take(8)}",
      type = "CAMERA_CONTROL",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendAppBlock(device: ChildDevice, packageName: String, isBlocked: Boolean, limitMinutes: Int = 0): Boolean {
    val payload = JSONObject().apply {
      put("packageName", packageName)
      put("isBlocked", isBlocked)
      put("limitMinutes", limitMinutes)
    }.toString()

    val cmd = ChildCommand(
      id = "app-${UUID.randomUUID().toString().take(8)}",
      type = "APP_BLOCK",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendDowntimeSync(device: ChildDevice, policy: DowntimePolicy): Boolean {
    val payload = JSONObject().apply {
      put("id", policy.id)
      put("name", policy.name)
      put("startTime", policy.startTime)
      put("endTime", policy.endTime)
      put("daysOfWeek", policy.daysOfWeek)
      put("isEnabled", policy.isEnabled)
    }.toString()

    val cmd = ChildCommand(
      id = "dwn-${UUID.randomUUID().toString().take(8)}",
      type = "DOWNTIME_SET",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendInstantBlockSync(device: ChildDevice, policy: InstantBlockPolicy): Boolean {
    val payload = JSONObject().apply {
      put("isActive", policy.isActive)
      put("blockUntilTimestamp", policy.blockUntilTimestamp)
      put("durationOption", policy.durationOption)
    }.toString()

    val cmd = ChildCommand(
      id = "blk-${UUID.randomUUID().toString().take(8)}",
      type = "INSTANT_BLOCK_SET",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  suspend fun sendFocusModeSync(device: ChildDevice, policy: FocusModePolicy): Boolean {
    val payload = JSONObject().apply {
      put("isActive", policy.isActive)
      put("durationMinutes", policy.durationMinutes)
      put("startedAtTimestamp", policy.startedAtTimestamp)
    }.toString()

    val cmd = ChildCommand(
      id = "foc-${UUID.randomUUID().toString().take(8)}",
      type = "FOCUS_MODE_SET",
      childDeviceId = device.id,
      timestamp = System.currentTimeMillis(),
      payload = payload
    )
    val result = executeCommand(cmd, device)
    return result.status == "SUCCESS"
  }

  // --- Real Audio Stream: Strictly without synthetic feedback generation ---
  fun startOneWayAudioStream(device: ChildDevice) {
    if (isAudioStreaming) return
    isAudioStreaming = true

    audioPlaybackJob = scope.launch(Dispatchers.IO) {
      try {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setSampleRate(sampleRate)
              .setEncoding(audioFormat)
              .setChannelMask(channelConfig)
              .build()
          )
          .setBufferSizeInBytes(bufferSize)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()

        audioTrack = track
        track.play()

        val audioUrl = getBaseUrl(device) + "/api/audio/stream"
        val request = Request.Builder().url(audioUrl).get().build()

        val buffer = ShortArray(1024)

        while (isActive && isAudioStreaming) {
          try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
              val inputStream = response.body!!.byteStream()
              val byteBuffer = ByteArray(2048)
              var bytesRead = 0
              while (isActive && isAudioStreaming && inputStream.read(byteBuffer).also { bytesRead = it } != -1) {
                var sumSquares = 0.0
                for (i in 0 until bytesRead / 2) {
                  val sample = ((byteBuffer[i * 2 + 1].toInt() shl 8) or (byteBuffer[i * 2].toInt() and 0xFF)).toShort()
                  buffer[i] = sample
                  sumSquares += sample * sample
                }
                track.write(buffer, 0, bytesRead / 2)
                val rms = Math.sqrt(sumSquares / (bytesRead / 2).coerceAtLeast(1))
                val db = (20 * Math.log10(rms.coerceAtLeast(1.0))).toFloat()
                _audioVolumeDb.value = (db - 20f).coerceIn(0f, 60f)
              }
              response.close()
            } else {
              response.close()
              _audioVolumeDb.value = 0f
              delay(500)
            }
          } catch (e: IOException) {
            _audioVolumeDb.value = 0f
            delay(1000)
          }
        }
      } catch (e: Exception) {
        AppLogger.log(AppLogger.Category.ERROR, "Audio stream error", e)
      } finally {
        stopOneWayAudio()
      }
    }
  }

  fun stopOneWayAudio() {
    isAudioStreaming = false
    audioPlaybackJob?.cancel()
    audioPlaybackJob = null
    try {
      audioTrack?.stop()
      audioTrack?.release()
    } catch (e: Exception) {
      // ignore safely
    }
    audioTrack = null
    _audioVolumeDb.value = 0f
  }

  suspend fun pairLocalChild(ipAddress: String, pairingCode: String): ChildDevice? = withContext(Dispatchers.IO) {
    try {
      val cleanIp = ipAddress.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
      val url = "http://$cleanIp:8888/api/pair?code=" + java.net.URLEncoder.encode(pairingCode.trim(), "UTF-8")
      val response = okHttpClient.newCall(Request.Builder().url(url).get().build()).execute()
      val body = response.body?.string().orEmpty()
      val code = response.code
      response.close()
      if (!response.isSuccessful || body.isBlank()) return@withContext null
      val json = JSONObject(body)
      val id = json.optString("deviceId")
      val token = json.optString("authToken")
      if (id.isBlank() || token.isBlank()) return@withContext null
      authTokens[id] = token
      ChildDevice(
        id = id, name = json.optString("name", "Child Device"), model = json.optString("model", "Android"),
        osVersion = json.optString("osVersion", "Android"), batteryPercent = 0, isCharging = false,
        storageUsedGb = 0.0, storageTotalGb = 0.0, isOnline = true, wifiSsid = "", wifiSignalDbm = 0,
        ipAddress = cleanIp, connectionMode = ConnectionMode.LOCAL_P2P, lastSeen = "Online now", authToken = token
      )
    } catch (_: Exception) { null }
  }

  fun getBaseUrl(device: ChildDevice): String {
    return if (device.connectionMode == ConnectionMode.LOCAL_P2P) {
      "http://${device.ipAddress}:8888"
    } else {
      "https://relay.airdroid.net/tunnel/${device.id}"
    }
  }

  suspend fun fetchDeviceCapabilities(device: ChildDevice): DevicePermissionHealth? = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/capabilities"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val json = JSONObject(str)
        DevicePermissionHealth(
          accessibilityService = json.optBoolean("accessibilityService", false),
          screenCastService = json.optBoolean("screenCastService", false),
          notificationListener = json.optBoolean("notificationListener", false),
          cameraAndMic = json.optBoolean("cameraAndMic", false),
          locationAlways = json.optBoolean("locationAlways", false),
          deviceAdmin = true,
          batteryOptimizationDisabled = true
        )
      } else {
        response.close()
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  suspend fun fetchLocation(device: ChildDevice): ChildLocation? = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/location"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val json = JSONObject(str)
        if (!json.optBoolean("hasLocation", false)) return@withContext null
        ChildLocation(
          deviceId = device.id,
          latitude = json.getDouble("latitude"),
          longitude = json.getDouble("longitude"),
          address = json.optString("address", "Child Location"),
          timestamp = json.optString("timestamp", "Recent"),
          accuracyMeters = json.optDouble("accuracyMeters", 10.0).toFloat(),
          batteryAtLocation = device.batteryPercent,
          isMoving = json.optBoolean("isMoving", false)
        )
      } else {
        response.close()
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  suspend fun fetchChildApps(device: ChildDevice): List<ManagedApp> = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/apps/usage"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val array = org.json.JSONArray(str)
        val apps = mutableListOf<ManagedApp>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          apps.add(
            ManagedApp(
              packageName = obj.optString("packageName"),
              deviceId = device.id,
              appName = obj.optString("name"),
              category = obj.optString("category", "Utility"),
              usageMinutesToday = obj.optInt("usageMinutesToday", 0),
              isBlocked = obj.optBoolean("isBlocked", false),
              dailyLimitMinutes = 0,
              isAlwaysAllowed = obj.optBoolean("isAlwaysAllowed", false)
            )
          )
        }
        apps
      } else {
        response.close()
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  suspend fun fetchChildFiles(device: ChildDevice, path: String = "/"): List<FileItem> = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/files/list?path=$path"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val array = org.json.JSONArray(str)
        val files = mutableListOf<FileItem>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val typeStr = obj.optString("fileType", "OTHER")
          val fileType = try { FileType.valueOf(typeStr) } catch (e: Exception) { FileType.OTHER }
          files.add(
            FileItem(
              id = obj.optString("id"),
              deviceId = device.id,
              name = obj.optString("name"),
              path = obj.optString("path"),
              isDirectory = obj.optBoolean("isDirectory"),
              sizeBytes = obj.optLong("sizeBytes"),
              modifiedDate = "Recent",
              fileType = fileType,
              isLocal = false
            )
          )
        }
        files
      } else {
        response.close()
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  suspend fun fetchChildNotifications(device: ChildDevice): List<RemoteNotification> = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/notifications"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val array = org.json.JSONArray(str)
        val notifs = mutableListOf<RemoteNotification>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          notifs.add(
            RemoteNotification(
              id = obj.optString("id"),
              deviceId = device.id,
              appName = obj.optString("appName"),
              packageName = obj.optString("packageName"),
              title = obj.optString("title"),
              content = obj.optString("content"),
              time = obj.optString("time"),
              isRead = false
            )
          )
        }
        notifs
      } else {
        response.close()
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  suspend fun fetchChildCalls(device: ChildDevice): List<CallLog> = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/calls"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val array = org.json.JSONArray(str)
        val calls = mutableListOf<CallLog>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val typeStr = obj.optString("callType", "INCOMING")
          val callType = try { CallType.valueOf(typeStr) } catch (e: Exception) { CallType.INCOMING }
          calls.add(
            CallLog(
              id = obj.optString("id"),
              deviceId = device.id,
              contactName = obj.optString("contactName"),
              phoneNumber = obj.optString("phoneNumber"),
              callType = callType,
              time = obj.optString("time", "Today"),
              durationSeconds = obj.optInt("durationSeconds", 0)
            )
          )
        }
        calls
      } else {
        response.close()
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  suspend fun fetchChildSms(device: ChildDevice): List<SmsThread> = withContext(Dispatchers.IO) {
    try {
      val url = "${getBaseUrl(device)}/api/sms"
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful && response.body != null) {
        val str = response.body!!.string()
        response.close()
        val array = org.json.JSONArray(str)
        val threads = mutableListOf<SmsThread>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          threads.add(
            SmsThread(
              id = obj.optString("id"),
              deviceId = device.id,
              contactName = obj.optString("contactName"),
              phoneNumber = obj.optString("phoneNumber"),
              lastMessage = obj.optString("lastMessage"),
              lastMessageTime = obj.optString("lastMessageTime", "Recent"),
              unreadCount = 0
            )
          )
        }
        threads
      } else {
        response.close()
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun streamChildFrames(
    device: ChildDevice, endpoint: String, onFrameReceived: (Bitmap) -> Unit, onError: (String) -> Unit
  ): Job = scope.launch(Dispatchers.IO) {
    try {
      while (isActive) {
        val request = Request.Builder().url(getBaseUrl(device) + endpoint + "?t=" + System.currentTimeMillis()).header("X-AirDroid-Auth", "Bearer ${authTokens[device.id] ?: device.authToken}").get().build()
        val response = okHttpClient.newCall(request).execute()
        val code = response.code
        val bytes = if (response.isSuccessful) response.body?.bytes() else null
        response.close()
        if (bytes != null && bytes.isNotEmpty()) {
          val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          if (bitmap != null) withContext(Dispatchers.Main) { onFrameReceived(bitmap) }
        } else if (code == 401 || code == 403) {
          withContext(Dispatchers.Main) { onError("AUTH_OR_PERMISSION_REQUIRED: Child rejected the stream request.") }
          return@launch
        } else if (code != 204) {
          withContext(Dispatchers.Main) { onError("Stream unavailable (HTTP $code)") }
          return@launch
        }
        delay(100)
      }
    } catch (e: Exception) {
      if (isActive) withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Stream connection failed") }
    }
  }

  fun streamMjpeg(
    url: String,
    onFrameReceived: (Bitmap) -> Unit,
    onError: (String) -> Unit
  ): Job = scope.launch(Dispatchers.IO) {
    try {
      val request = Request.Builder().url(url).get().build()
      val response = okHttpClient.newCall(request).execute()
      if (!response.isSuccessful || response.body == null) {
        if (response.code == 403) {
          onError("PERMISSION_REQUIRED: Feature permission not granted on Child device.")
        } else {
          onError("Stream unreachable (HTTP ${response.code})")
        }
        response.close()
        return@launch
      }

      val inputStream = response.body!!.byteStream()
      val streamBuffer = java.io.ByteArrayOutputStream()
      val readBuffer = ByteArray(4096)
      var prevByte = 0

      while (isActive) {
        val read = inputStream.read(readBuffer)
        if (read < 0) break
        for (i in 0 until read) {
          val b = readBuffer[i].toInt() and 0xFF
          streamBuffer.write(b)
          if (prevByte == 0xFF && b == 0xD9) { // JPEG End of Image SOI ... EOI
            val jpegBytes = streamBuffer.toByteArray()
            streamBuffer.reset()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            if (bitmap != null) {
              withContext(Dispatchers.Main) {
                onFrameReceived(bitmap)
              }
            }
          }
          prevByte = b
        }
      }
      response.close()
    } catch (e: Exception) {
      if (isActive) {
        onError(e.localizedMessage ?: "Stream connection failed")
      }
    }
  }
}

