package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AirDroidChildApp
import com.example.data.model.DaemonLog
import com.example.data.model.DaemonStatus
import com.example.data.model.PairedParentDevice
import com.example.service.AirDroidAccessibilityService
import com.example.ui.theme.AirDroidBlue
import com.example.ui.theme.AirDroidCyan
import com.example.ui.theme.AirDroidGreen
import com.example.ui.theme.AirDroidOrange
import com.example.ui.theme.AirDroidRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    daemonStatus: DaemonStatus,
    activeDevice: PairedParentDevice?,
    connectedClients: Int,
    touchesExecuted: Int,
    notificationsSynced: Int,
    recentLogs: List<DaemonLog>,
    onToggleDaemon: () -> Unit,
    onPauseResume: () -> Unit,
    onOpenParentSimulator: () -> Unit,
    onOpenCamouflageCalculator: () -> Unit,
    onOpenWizard: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val screenCastEngine = remember { AirDroidChildApp.screenCastEngine }
    val cameraManager = remember { AirDroidChildApp.cameraManager }
    val audioManager = remember { AirDroidChildApp.audioManager }
    val locationManager = remember { AirDroidChildApp.locationManager }
    val stealthManager = remember { AirDroidChildApp.stealthManager }

    val isScreenCasting by (screenCastEngine?.isStreaming?.collectAsState() ?: remember { mutableStateOf(false) })
    val isCameraStreaming by (cameraManager?.isStreaming?.collectAsState() ?: remember { mutableStateOf(false) })
    val isAudioListening by (audioManager?.isRecording?.collectAsState() ?: remember { mutableStateOf(false) })
    val ambientDecibels by (audioManager?.ambientDecibels?.collectAsState() ?: remember { mutableStateOf(0f) })
    val childLocation by (locationManager?.currentLocation?.collectAsState() ?: remember { mutableStateOf(null) })

    val isCamouflageEnabled by (stealthManager?.isCamouflageEnabled?.collectAsState() ?: remember { mutableStateOf(false) })
    val isAutoClickerActive by (stealthManager?.isAutoClickerActive?.collectAsState() ?: remember { mutableStateOf(true) })
    val isAppIconHidden by (stealthManager?.isAppIconHidden?.collectAsState() ?: remember { mutableStateOf(false) })
    val autoGrantedCount by (AirDroidAccessibilityService.autoGrantedCount.collectAsState())

    val localIp = AirDroidChildApp.daemonServer?.getLocalIpAddress() ?: "127.0.0.1"
    val webConsoleUrl = "http://$localIp:8888/parent"

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val metrics = context.resources.displayMetrics
            screenCastEngine?.startCasting(result.resultCode, result.data!!, metrics)
            Toast.makeText(context, "AirMirror Screen stream activated", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("dashboard_screen")
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AirDroidCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AirDroidCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "AirDroid Child Daemon",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Silent Background Host Active",
                            fontSize = 11.sp,
                            color = AirDroidGreen
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onOpenWizard) {
                    Icon(Icons.Default.Security, contentDescription = "Permissions", tint = AirDroidCyan)
                }
                IconButton(onClick = onOpenCamouflageCalculator) {
                    Icon(Icons.Default.Calculate, contentDescription = "Camouflage", tint = AirDroidOrange)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                DaemonStatusCard(
                    status = daemonStatus,
                    localIp = localIp,
                    webConsoleUrl = webConsoleUrl,
                    connectedClients = connectedClients,
                    onPauseResume = onPauseResume,
                    onToggleDaemon = onToggleDaemon
                )
            }

            // Stealth & Invisibility Mode Control Card
            item {
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
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = AirDroidCyan, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Stealth & Invisible Operation", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Text("Auto-grant & Calculator Disguise", fontSize = 11.sp, color = AirDroidGreen)
                                }
                            }
                            Button(
                                onClick = onOpenCamouflageCalculator,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = AirDroidCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Calculator", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 1: Auto-Clicker Self Granting
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Click Permission Service", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Auto-clicks 'Allow' & 'Start now' ($autoGrantedCount granted)", fontSize = 11.sp, color = AirDroidGreen)
                            }
                            Switch(
                                checked = isAutoClickerActive,
                                onCheckedChange = { stealthManager?.setAutoClickerActive(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AirDroidGreen, checkedTrackColor = AirDroidGreen.copy(alpha = 0.3f))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switch 2: Calculator Camouflage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Disguise as Secret Calculator", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Opens calculator; PIN 1234= unlocks dashboard", fontSize = 11.sp, color = TextTertiary)
                            }
                            Switch(
                                checked = isCamouflageEnabled,
                                onCheckedChange = { stealthManager?.setCamouflageEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AirDroidOrange, checkedTrackColor = AirDroidOrange.copy(alpha = 0.3f))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switch 3: Hide App Icon from Launcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Invisible App Icon (Stealth)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Hides app from launcher drawer", fontSize = 11.sp, color = TextTertiary)
                            }
                            Switch(
                                checked = isAppIconHidden,
                                onCheckedChange = {
                                    stealthManager?.setAppIconHidden(it)
                                    Toast.makeText(context, if (it) "App icon hidden from launcher" else "App icon visible in launcher", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AirDroidCyan, checkedTrackColor = AirDroidCyan.copy(alpha = 0.3f))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full Screen Cast Setup Action
                        Button(
                            onClick = {
                                val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                Toast.makeText(context, "Accessibility service will auto-select 'Entire Screen' & 'Start now'", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScreenCasting) AirDroidGreen.copy(alpha = 0.2f) else AirDroidCyan,
                                contentColor = if (isScreenCasting) AirDroidGreen else DarkBackground
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (isScreenCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isScreenCasting) "Entire Screen Mirroring Active" else "Pair Screen Share (Auto-Click 'Entire Screen')",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Invisible Mode Button ("Pura app gayab ho jayega or background me chalega")
                        Button(
                            onClick = {
                                stealthManager?.setAppIconHidden(true)
                                Toast.makeText(
                                    context,
                                    "Invisible Mode Active: App icon hidden, running silently in background. Auto-restarts on reboot!",
                                    Toast.LENGTH_LONG
                                ).show()
                                (context as? Activity)?.moveTaskToBack(true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AirDroidOrange,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Make App Invisible & Run in Background",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Ambient Audio & GPS Tracking Cards
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Ambient Sound Tile
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAudioListening) AirDroidGreen.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAudioListening) AirDroidGreen else DarkCardBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isAudioListening) {
                                    audioManager?.stopListening()
                                } else {
                                    val ok = audioManager?.startListening() ?: false
                                    if (ok) Toast.makeText(context, "Listening to surroundings", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (isAudioListening) AirDroidGreen else DarkSurfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = if (isAudioListening) DarkBackground else TextPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isAudioListening) "Audio Active (${ambientDecibels.toInt()} dB)" else "Surround Sound",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAudioListening) "Streaming mic live" else "Tap to listen mic",
                                fontSize = 11.sp,
                                color = if (isAudioListening) AirDroidGreen else TextSecondary
                            )
                        }
                    }

                    // GPS Location Tile
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                locationManager?.startTracking()
                                Toast.makeText(context, "Refreshing GPS satellite coordinates", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = AirDroidCyan, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (childLocation != null) "GPS: %.3f, %.3f".format(childLocation!!.latitude, childLocation!!.longitude) else "Live GPS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (childLocation != null) "Acc: ±${childLocation!!.accuracy.toInt()}m" else "Tap to ping GPS",
                                fontSize = 11.sp,
                                color = AirDroidCyan
                            )
                        }
                    }
                }
            }

            // Action Quick Links
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webConsoleUrl))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not launch browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_web_console_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AirDroidCyan,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Web Console", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenParentSimulator,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_parent_simulator_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AirDroidGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AirDroidGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DesktopWindows, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Parent Sim", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Quick Service Engines
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Screen Casting Toggle
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isScreenCasting) AirDroidCyan.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isScreenCasting) AirDroidCyan else DarkCardBorder
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isScreenCasting) {
                                    screenCastEngine?.stopCasting()
                                } else {
                                    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isScreenCasting) AirDroidCyan else DarkSurfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isScreenCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = if (isScreenCasting) DarkBackground else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isScreenCasting) "AirMirror Active" else "Screen Mirroring",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isScreenCasting) "Casting display" else "Tap to start cast",
                                fontSize = 11.sp,
                                color = if (isScreenCasting) AirDroidCyan else TextSecondary
                            )
                        }
                    }

                    // Remote Camera Toggle
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCameraStreaming) AirDroidOrange.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCameraStreaming) AirDroidOrange else DarkCardBorder
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isCameraStreaming) {
                                    cameraManager?.stopStreaming()
                                } else {
                                    cameraManager?.startStreaming(false)
                                    Toast.makeText(context, "Remote Camera daemon active", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isCameraStreaming) AirDroidOrange else DarkSurfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (isCameraStreaming) Color(0xFF421D00) else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isCameraStreaming) "Camera Streaming" else "Remote Camera",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isCameraStreaming) "Background feed on" else "Tap to activate",
                                fontSize = 11.sp,
                                color = if (isCameraStreaming) AirDroidOrange else TextSecondary
                            )
                        }
                    }
                }
            }

            // Real-time Metrics Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DAEMON TELEMETRY & METRICS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricItem(
                                label = "Automated Touches",
                                value = "$touchesExecuted",
                                color = AirDroidCyan,
                                icon = Icons.Default.TouchApp
                            )
                            MetricItem(
                                label = "Auto Permissions",
                                value = "$autoGrantedCount",
                                color = AirDroidGreen,
                                icon = Icons.Default.FlashOn
                            )
                            MetricItem(
                                label = "Mirrored Alerts",
                                value = "$notificationsSynced",
                                color = AirDroidOrange,
                                icon = Icons.Default.Computer
                            )
                        }
                    }
                }
            }

            // Paired Device Info
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PAIRED PARENT CONSOLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextTertiary,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = if (connectedClients > 0) "🟢 Duplex Connected" else "🟡 Standby / Listening",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (connectedClients > 0) SuccessGreen else AirDroidOrange
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = activeDevice?.deviceName ?: "Parent Web Console (web.airdroid.com)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Platform: ${activeDevice?.platform ?: "Desktop Browser"} • Port: 8888",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pairing Code: ${activeDevice?.pairingCode ?: "READY"}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AirDroidCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Silent Daemon 24/7",
                                fontSize = 11.sp,
                                color = AirDroidGreen
                            )
                        }
                    }
                }
            }

            // Audit Trail Header & Log List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AUDIT TRAIL / REMOTE COMMAND LOG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )

                    IconButton(onClick = onClearLogs, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (recentLogs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No remote commands recorded yet", color = TextSecondary, fontSize = 13.sp)
                            Text("Touches, screen mirrors, and file actions will appear here in real time.", color = TextTertiary, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(recentLogs) { log ->
                    LogCard(log = log)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DaemonStatusCard(
    status: DaemonStatus,
    localIp: String,
    webConsoleUrl: String,
    connectedClients: Int,
    onPauseResume: () -> Unit,
    onToggleDaemon: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = when (status) {
        DaemonStatus.CONNECTED -> SuccessGreen
        DaemonStatus.LISTENING -> AirDroidCyan
        DaemonStatus.PAUSED -> AirDroidOrange
        DaemonStatus.STOPPED -> AirDroidRed
        DaemonStatus.INITIALIZING -> AirDroidBlue
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STATUS: ${status.name}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = statusColor,
                        letterSpacing = 1.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pause / Resume button
                    IconButton(
                        onClick = onPauseResume,
                        modifier = Modifier
                            .size(34.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (status == DaemonStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause / Resume",
                            tint = if (status == DaemonStatus.PAUSED) SuccessGreen else AirDroidOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Stop / Start daemon
                    IconButton(
                        onClick = onToggleDaemon,
                        modifier = Modifier
                            .size(34.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Service",
                            tint = if (status == DaemonStatus.STOPPED) AirDroidCyan else AirDroidRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Daemon Endpoint & WebSocket",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = webConsoleUrl,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = "WebSocket duplex on /ws (Port 8888)",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }

                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("AirDroid Parent URL", webConsoleUrl))
                        Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = AirDroidCyan, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "⚡ START_STICKY persistent lifecycle active with Android WakeLock. Auto-restarts on reboot.",
                fontSize = 11.sp,
                color = AirDroidGreen
            )
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextPrimary)
        Text(label, fontSize = 10.sp, color = TextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun LogCard(log: DaemonLog) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { formatter.format(Date(log.timestamp)) }

    val typeColor = when (log.actionType) {
        "REMOTE_TOUCH", "TOUCH", "REMOTE_SWIPE" -> AirDroidCyan
        "AUTO_GRANT_CLICK", "AUTO_SWITCH_TOGGLE" -> AirDroidGreen
        "SCREEN_CAST", "AIRMIRROR" -> AirDroidBlue
        "NOTIFICATION" -> AirDroidOrange
        "CAMERA" -> Color(0xFFFF4081)
        else -> TextSecondary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(typeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = log.actionType.take(12),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.details,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${log.sourceDevice} • $timeString",
                    fontSize = 10.sp,
                    color = TextTertiary
                )
            }

            if (log.isSuccess) {
                Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text("✕", color = AirDroidRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
