package com.example.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.AirDroidChildApp
import com.example.R
import com.example.data.model.FileItem
import com.example.data.model.SyncedNotification
import com.example.ui.theme.AirDroidBlue
import com.example.ui.theme.AirDroidCyan
import com.example.ui.theme.AirDroidGreen
import com.example.ui.theme.AirDroidOrange
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSimulatorScreen(
    notifications: List<SyncedNotification>,
    onSendQuickReply: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val touchEngine = remember { AirDroidChildApp.touchEngine }
    val screenCastEngine = remember { AirDroidChildApp.screenCastEngine }
    val cameraManager = remember { AirDroidChildApp.cameraManager }
    val fileManager = remember { AirDroidChildApp.fileManager }
    val audioManager = remember { AirDroidChildApp.audioManager }
    val locationManager = remember { AirDroidChildApp.locationManager }
    val stealthManager = remember { AirDroidChildApp.stealthManager }

    val screenFrame by (screenCastEngine?.latestFrame?.collectAsState() ?: remember { mutableStateOf(null) })
    val cameraFrame by (cameraManager?.latestFrame?.collectAsState() ?: remember { mutableStateOf(null) })
    val isCameraStreaming by (cameraManager?.isStreaming?.collectAsState() ?: remember { mutableStateOf(false) })
    val isAudioListening by (audioManager?.isRecording?.collectAsState() ?: remember { mutableStateOf(false) })
    val ambientDecibels by (audioManager?.ambientDecibels?.collectAsState() ?: remember { mutableStateOf(0f) })
    val childLocation by (locationManager?.currentLocation?.collectAsState() ?: remember { mutableStateOf(null) })
    val blockedPackages by (stealthManager?.blockedPackages?.collectAsState() ?: remember { mutableStateOf(emptySet()) })

    var lastSimulatedTouch by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var currentBrowsePath by remember { mutableStateOf("") }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Initialize files
    remember(currentBrowsePath) {
        fileList = if (currentBrowsePath.isEmpty()) {
            fileManager?.getDefaultDirectories() ?: emptyList()
        } else {
            fileManager?.listDirectory(currentBrowsePath) ?: emptyList()
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("parent_simulator_screen")
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(AirDroidGreen.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💻", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Parent Console Simulator",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Simulating Desktop / Web Client",
                            fontSize = 11.sp,
                            color = AirDroidCyan
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = AirDroidCyan,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AirDroidCyan,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("AirMirror Touch", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Surroundings & GPS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("App Blocker", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Notifications (${notifications.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Camera", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 5,
                onClick = { selectedTab = 5 },
                text = { Text("File Manager", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        when (selectedTab) {
            0 -> TouchAndScreenTab(
                screenFrame = screenFrame,
                lastTouch = lastSimulatedTouch,
                onTouchSimulated = { normX, normY ->
                    lastSimulatedTouch = Pair(normX, normY)
                    touchEngine?.performClick(normX, normY, isNormalized = true)
                    Toast.makeText(context, "Dispatched touch ($normX, $normY)", Toast.LENGTH_SHORT).show()
                },
                onLongPressSimulated = { normX, normY ->
                    lastSimulatedTouch = Pair(normX, normY)
                    touchEngine?.performLongPress(normX, normY, isNormalized = true)
                    Toast.makeText(context, "Dispatched Long Press ($normX, $normY)", Toast.LENGTH_SHORT).show()
                },
                onSwipeSimulated = { x1, y1, x2, y2 ->
                    touchEngine?.performSwipe(x1, y1, x2, y2, durationMs = 350, isNormalized = true)
                    Toast.makeText(context, "Dispatched swipe gesture", Toast.LENGTH_SHORT).show()
                },
                onKeyDispatched = { key ->
                    touchEngine?.performKeyEvent(key)
                    Toast.makeText(context, "Key: $key", Toast.LENGTH_SHORT).show()
                },
                onTextInjected = { text ->
                    val success = touchEngine?.performTextInjection(text) ?: false
                    Toast.makeText(context, if (success) "Text injected: \"$text\"" else "No active input field focused", Toast.LENGTH_SHORT).show()
                }
            )
            1 -> SurroundingsAndGpsTab(
                isAudioListening = isAudioListening,
                ambientDecibels = ambientDecibels,
                onToggleAudio = {
                    if (isAudioListening) {
                        audioManager?.stopListening()
                    } else {
                        val ok = audioManager?.startListening() ?: false
                        if (ok) Toast.makeText(context, "Listening to surroundings", Toast.LENGTH_SHORT).show()
                    }
                },
                location = childLocation,
                onPingGps = {
                    locationManager?.startTracking()
                    Toast.makeText(context, "Pinging child device GPS...", Toast.LENGTH_SHORT).show()
                }
            )
            2 -> AppBlockerTab(
                blockedPackages = blockedPackages,
                onToggleBlock = { pkg, block ->
                    if (block) stealthManager?.addBlockedPackage(pkg) else stealthManager?.removeBlockedPackage(pkg)
                },
                onLaunchApp = { pkg ->
                    val ok = touchEngine?.launchPackage(pkg) ?: false
                    Toast.makeText(context, if (ok) "Remotely launched $pkg" else "Failed to launch $pkg", Toast.LENGTH_SHORT).show()
                }
            )
            3 -> NotificationsTab(
                notifications = notifications,
                onSendQuickReply = onSendQuickReply,
                onTriggerTestNotification = {
                    sendTestNotification(context)
                }
            )
            4 -> RemoteCameraTab(
                cameraFrame = cameraFrame,
                isStreaming = isCameraStreaming,
                onToggleStream = {
                    if (isCameraStreaming) {
                        cameraManager?.stopStreaming()
                    } else {
                        cameraManager?.startStreaming(false)
                    }
                },
                onSwitchLens = {
                    val current = cameraManager?.currentLensFacing?.value ?: "BACK"
                    cameraManager?.startStreaming(current != "FRONT")
                }
            )
            5 -> FileManagerTab(
                currentPath = currentBrowsePath,
                files = fileList,
                onNavigate = { newPath -> currentBrowsePath = newPath },
                onBackDir = {
                    val parent = currentBrowsePath.substringBeforeLast('/', "")
                    currentBrowsePath = parent
                }
            )
        }
    }
}

@Composable
fun TouchAndScreenTab(
    screenFrame: ByteArray?,
    lastTouch: Pair<Float, Float>?,
    onTouchSimulated: (Float, Float) -> Unit,
    onLongPressSimulated: (Float, Float) -> Unit,
    onSwipeSimulated: (Float, Float, Float, Float) -> Unit,
    onKeyDispatched: (String) -> Unit,
    onTextInjected: (String) -> Unit
) {
    var dragStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Virtual Touch Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(2.dp, AirDroidCyan, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val normX = offset.x / size.width
                            val normY = offset.y / size.height
                            onTouchSimulated(normX, normY)
                        },
                        onLongPress = { offset ->
                            val normX = offset.x / size.width
                            val normY = offset.y / size.height
                            onLongPressSimulated(normX, normY)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = Pair(offset.x / size.width, offset.y / size.height)
                        },
                        onDragEnd = {
                            dragStart = null
                        },
                        onDrag = { change, _ ->
                            val start = dragStart
                            if (start != null) {
                                val currentX = change.position.x / size.width
                                val currentY = change.position.y / size.height
                                onSwipeSimulated(start.first, start.second, currentX, currentY)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (screenFrame != null) {
                val bitmap = remember(screenFrame) {
                    BitmapFactory.decodeByteArray(screenFrame, 0, screenFrame.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Screen stream",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = AirDroidCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "AirMirror Touch Canvas",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap or drag to dispatch touches/swipes. Hold to Long Press.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Visual Touch Reticle feedback
            if (lastTouch != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = (lastTouch.first * 180).dp, top = (lastTouch.second * 320).dp)
                        .size(24.dp)
                        .border(2.dp, AirDroidGreen, CircleShape)
                        .background(AirDroidGreen.copy(alpha = 0.3f), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Remote Text Injection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type text to inject into child phone...", fontSize = 12.sp, color = TextTertiary) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AirDroidCyan,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (textInput.isNotEmpty()) {
                        onTextInjected(textInput)
                        textInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AirDroidCyan, contentColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Type", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hardware Controls Bar (Home, Back, Recents, Lock, Volume)
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlKeyButton(label = "🏠 Home", onClick = { onKeyDispatched("HOME") })
                ControlKeyButton(label = "◀ Back", onClick = { onKeyDispatched("BACK") })
                ControlKeyButton(label = "📑 Recents", onClick = { onKeyDispatched("RECENTS") })
                ControlKeyButton(label = "🔒 Lock", onClick = { onKeyDispatched("LOCK") })
                ControlKeyButton(label = "🔊 Vol+", onClick = { onKeyDispatched("VOLUME_UP") })
                ControlKeyButton(label = "🔉 Vol-", onClick = { onKeyDispatched("VOLUME_DOWN") })
            }
        }
    }
}

@Composable
fun SurroundingsAndGpsTab(
    isAudioListening: Boolean,
    ambientDecibels: Float,
    onToggleAudio: () -> Unit,
    location: com.example.engine.ChildLocation?,
    onPingGps: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // One-way Ambient Sound Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAudioListening) AirDroidGreen else DarkCardBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isAudioListening) AirDroidGreen else DarkSurfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = if (isAudioListening) DarkBackground else AirDroidCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Surroundings Audio (Mic)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text(if (isAudioListening) "Live Streaming Ambient Audio" else "Standby", fontSize = 11.sp, color = if (isAudioListening) AirDroidGreen else TextTertiary)
                        }
                    }

                    Button(
                        onClick = onToggleAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAudioListening) AirDroidOrange else AirDroidGreen,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isAudioListening) "Stop Listening" else "Listen Surroundings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isAudioListening) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Ambient Sound Level: ${ambientDecibels.toInt()} dB", fontSize = 12.sp, color = AirDroidGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (ambientDecibels / 100f).coerceIn(0.05f, 1f))
                                .fillMaxSize()
                                .background(AirDroidGreen)
                        )
                    }
                }
            }
        }

        // Real-Time GPS Tracking Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, AirDroidCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AirDroidCyan.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = AirDroidCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Real-Time GPS Location", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text(if (location != null) "Accurate within ±${location.accuracy.toInt()}m" else "Acquiring Fix...", fontSize = 11.sp, color = TextTertiary)
                        }
                    }

                    Button(
                        onClick = onPingGps,
                        colors = ButtonDefaults.buttonColors(containerColor = AirDroidCyan, contentColor = DarkBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ping GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (location != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Latitude: ${location.latitude}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("Longitude: ${location.longitude}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("Speed: ${location.speed} m/s • Provider: ${location.provider}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                } else {
                    Text("Standby: Tap 'Ping GPS' to pull satellite fix from child device.", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AppBlockerTab(
    blockedPackages: Set<String>,
    onToggleBlock: (String, Boolean) -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("youtube") || it.packageName.contains("chrome") }
            .map { Pair(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.first }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "CHILD APP MANAGEMENT & RESTRICTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 1.sp
            )
        }

        items(apps) { (name, pkg) ->
            val isBlocked = blockedPackages.contains(pkg)

            Card(
                colors = CardDefaults.cardColors(containerColor = if (isBlocked) AirDroidOrange.copy(alpha = 0.1f) else DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isBlocked) AirDroidOrange else DarkCardBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Text(pkg, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                        if (isBlocked) {
                            Text("🚫 Restricted by Parent", fontSize = 10.sp, color = AirDroidOrange, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onLaunchApp(pkg) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = AirDroidCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Launch", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { onToggleBlock(pkg, !isBlocked) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBlocked) AirDroidGreen else AirDroidOrange,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (isBlocked) "Unblock" else "Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlKeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
fun NotificationsTab(
    notifications: List<SyncedNotification>,
    onSendQuickReply: (String, String) -> Unit,
    onTriggerTestNotification: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MIRRORED NOTIFICATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 1.sp
            )

            Button(
                onClick = onTriggerTestNotification,
                colors = ButtonDefaults.buttonColors(containerColor = AirDroidCyan, contentColor = Color(0xFF00363D)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Incoming", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No synced notifications yet", color = TextSecondary, fontSize = 14.sp)
                    Text("Tap 'Test Incoming' above to simulate a WhatsApp/SMS alert", color = TextTertiary, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications) { notif ->
                    NotificationItemCard(notification = notif, onSendReply = { reply ->
                        onSendQuickReply(notif.id, reply)
                    })
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: SyncedNotification,
    onSendReply: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${notification.appTitle} (${notification.packageName})",
                    fontSize = 11.sp,
                    color = AirDroidCyan,
                    fontWeight = FontWeight.Bold
                )

                if (notification.canReply) {
                    Box(
                        modifier = Modifier
                            .background(AirDroidGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("QUICK REPLY READY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AirDroidGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(notification.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Text(notification.text, fontSize = 12.sp, color = TextSecondary)

            if (notification.canReply) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Type quick reply...", fontSize = 12.sp, color = TextTertiary) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AirDroidCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onSendReply(replyText)
                                replyText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(AirDroidCyan, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = Color(0xFF00363D), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteCameraTab(
    cameraFrame: ByteArray?,
    isStreaming: Boolean,
    onToggleStream: () -> Unit,
    onSwitchLens: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(2.dp, AirDroidOrange, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cameraFrame != null && isStreaming) {
                val bitmap = remember(cameraFrame) {
                    BitmapFactory.decodeByteArray(cameraFrame, 0, cameraFrame.size)
                }
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Camera Feed", modifier = Modifier.fillMaxSize())
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AirDroidOrange, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isStreaming) "Camera feed active" else "Camera feed standby", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onToggleStream,
                colors = ButtonDefaults.buttonColors(containerColor = if (isStreaming) AirDroidOrange else AirDroidGreen, contentColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text(if (isStreaming) "Stop Camera" else "Start Camera Feed", fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onSwitchLens,
                enabled = isStreaming,
                modifier = Modifier
                    .size(44.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = if (isStreaming) AirDroidCyan else TextTertiary)
            }
        }
    }
}

@Composable
fun FileManagerTab(
    currentPath: String,
    files: List<FileItem>,
    onNavigate: (String) -> Unit,
    onBackDir: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackDir,
                enabled = currentPath.isNotEmpty(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back Directory", tint = if (currentPath.isNotEmpty()) AirDroidCyan else TextTertiary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentPath.isEmpty()) "Root / Internal Storage" else currentPath,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(files) { file ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = file.isDirectory) {
                            onNavigate(file.path)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (file.isDirectory) AirDroidCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            if (!file.isDirectory) {
                                Text("${file.sizeBytes / 1024} KB", fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sendTestNotification(context: Context) {
    val channelId = "parent_test_channel"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Test Mirror Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("WhatsApp • Mom")
        .setContentText("Dinner is ready! Please come home on time.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    manager.notify((1000..9999).random(), notification)
    Toast.makeText(context, "Test WhatsApp notification posted", Toast.LENGTH_SHORT).show()
}
