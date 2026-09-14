package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChildDevice
import com.example.ui.AirMirrorUiState
import com.example.ui.TouchPoint
import com.example.ui.theme.*

@Composable
fun AirMirrorScreen(
  device: ChildDevice?,
  airMirrorState: AirMirrorUiState,
  latencyMs: Int,
  fps: Int,
  onScreenTouch: (Float, Float) -> Unit,
  onScreenSwipe: (String) -> Unit,
  onHardwareButton: (String) -> Unit,
  onToggleOrientation: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onTakeScreenshot: () -> Unit,
  onToggleRecording: () -> Unit,
  onUpdateResolution: (String, Float) -> Unit,
  onTypeKey: (String) -> Unit,
  onBackspace: () -> Unit,
  onClearInput: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showSettingsDialog by remember { mutableStateOf(false) }
  var pcKeyboardBuffer by remember { mutableStateOf("") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(12.dp)
  ) {
    // Top Control Toolbar & Stream HUD
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Stream Status & Live Health HUD
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = AirDroidGreen.copy(alpha = 0.2f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(AirDroidGreen)
              )
              Text(
                text = "AirMirror Live",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AirDroidGreen
              )
            }
          }

          Text(
            text = "${airMirrorState.resolution} • ${airMirrorState.bitrateMbps} Mbps",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Text(
            text = "⚡ ${latencyMs}ms • $fps FPS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (latencyMs < 40) StatusOnline else StatusWarning
          )
        }

        // Right: Control Toolbar Actions
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Screenshot button
          IconButton(
            onClick = onTakeScreenshot,
            modifier = Modifier.size(32.dp).testTag("airmirror_screenshot_button")
          ) {
            Icon(
              imageVector = Icons.Filled.PhotoCamera,
              contentDescription = "Capture Screenshot to PC",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp)
            )
          }

          // Screen Recording button
          IconButton(
            onClick = onToggleRecording,
            modifier = Modifier.size(32.dp).testTag("airmirror_record_button")
          ) {
            Icon(
              imageVector = if (airMirrorState.isRecording) Icons.Filled.StopCircle else Icons.Filled.Videocam,
              contentDescription = "Record Screen",
              tint = if (airMirrorState.isRecording) StatusOffline else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp)
            )
          }

          // Orientation Toggle (Portrait / Landscape)
          IconButton(
            onClick = onToggleOrientation,
            modifier = Modifier.size(32.dp).testTag("airmirror_rotate_button")
          ) {
            Icon(
              imageVector = Icons.Filled.ScreenRotation,
              contentDescription = "Rotate Screen",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp)
            )
          }

          // Quality Settings
          IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.size(32.dp).testTag("airmirror_settings_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = "Resolution / Bitrate Settings",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp)
            )
          }

          // Fullscreen Toggle
          IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(32.dp).testTag("airmirror_fullscreen_button")
          ) {
            Icon(
              imageVector = if (airMirrorState.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
              contentDescription = "Toggle Fullscreen",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Main Canvas Stream Container & Hardware Button Rail
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Swipe & Scroll Mapping Gesture Controls
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 12.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.padding(2.dp)
        ) {
          Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("Scroll", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
              onClick = { onScreenSwipe("UP") },
              modifier = Modifier.size(32.dp).testTag("airmirror_swipe_up")
            ) {
              Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll Up", tint = AirDroidGreen)
            }
            IconButton(
              onClick = { onScreenSwipe("DOWN") },
              modifier = Modifier.size(32.dp).testTag("airmirror_swipe_down")
            ) {
              Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll Down", tint = AirDroidGreen)
            }
          }
        }
      }

      // Center: Simulated Phone Frame with Live Canvas Rendering
      val phoneWidth = if (airMirrorState.isLandscape) 480.dp else 260.dp
      val phoneHeight = if (airMirrorState.isLandscape) 260.dp else 490.dp

      Box(
        modifier = Modifier
          .width(phoneWidth)
          .height(phoneHeight)
          .shadow(16.dp, RoundedCornerShape(32.dp))
          .clip(RoundedCornerShape(32.dp))
          .background(Color(0xFF0F172A))
          .border(4.dp, Color(0xFF334155), RoundedCornerShape(32.dp))
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { offset ->
                val xPercent = (offset.x / size.width).coerceIn(0f, 1f)
                val yPercent = (offset.y / size.height).coerceIn(0f, 1f)
                onScreenTouch(xPercent, yPercent)
              }
            )
          }
          .testTag("airmirror_touch_canvas"),
        contentAlignment = Alignment.Center
      ) {
        // Child Device Screen Content
        Column(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color(0xFF0A192F), Color(0xFF1E293B), Color(0xFF0B192C))
              )
            )
        ) {
          // Status Bar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("10:42", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            // Camera cutout notch
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.Black)
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
              Text("${device?.batteryPercent ?: 84}%", fontSize = 10.sp, color = Color.White)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Mirrored Screen Live Stream or Notice
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            if (airMirrorState.currentFrame != null) {
              Image(
                bitmap = airMirrorState.currentFrame,
                contentDescription = "AirMirror Live Screen Feed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
              )
            } else if (airMirrorState.permissionError != null) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.ScreenShare,
                  contentDescription = null,
                  tint = StatusWarning,
                  modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  text = "Screen Mirroring Notice",
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = airMirrorState.permissionError,
                  color = Color(0xFF94A3B8),
                  fontSize = 11.sp,
                  textAlign = TextAlign.Center
                )
              }
            } else {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                CircularProgressIndicator(color = AirDroidGreen, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Connecting to Child Screen...", fontSize = 11.sp, color = Color(0xFF94A3B8))
              }
            }

            // Power Menu Overlay if active
            if (airMirrorState.isPowerMenuVisible) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xEE1E293B),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp)
                  .align(Alignment.Center)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceAround
                ) {
                  Text("🔴 Power Off", fontSize = 11.sp, color = Color.White)
                  Text("🔄 Restart", fontSize = 11.sp, color = Color.White)
                  Text("🔒 Lock", fontSize = 11.sp, color = Color.White)
                }
              }
            }
          }

          // Active volume indicator if changed
          Text(
            text = "Device Volume: ${airMirrorState.volumeLevel}%",
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.align(Alignment.CenterHorizontally)
          )

          // Bottom Android Navigation Bar (Home, Back, Recents)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0x66000000))
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = { onHardwareButton("BACK") }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onHardwareButton("HOME") }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Filled.Circle, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = { onHardwareButton("RECENTS") }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Filled.Square, contentDescription = "Recents", tint = Color.White, modifier = Modifier.size(14.dp))
            }
          }
        }

        // Touch Ripple / Input Visualizer Canvas Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
          airMirrorState.touchPoints.forEach { point ->
            drawCircle(
              color = Color(0x9900E5A3),
              radius = 24f,
              center = Offset(point.x * size.width, point.y * size.height)
            )
            drawCircle(
              color = Color(0xFFFFFFFF),
              radius = 8f,
              center = Offset(point.x * size.width, point.y * size.height)
            )
          }
        }
      }

      // Right: Dedicated Hardware Button Simulation Toolbar
      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = 12.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.padding(2.dp)
        ) {
          Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("Hardware", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
              onClick = { onHardwareButton("BACK") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_back")
            ) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Hardware Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
              onClick = { onHardwareButton("HOME") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_home")
            ) {
              Icon(Icons.Filled.Home, contentDescription = "Hardware Home", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
              onClick = { onHardwareButton("RECENTS") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_recents")
            ) {
              Icon(Icons.Filled.ViewQuilt, contentDescription = "Hardware Recents", tint = MaterialTheme.colorScheme.onSurface)
            }
            Divider(modifier = Modifier.width(24.dp).padding(vertical = 4.dp))
            IconButton(
              onClick = { onHardwareButton("VOLUME_UP") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_vol_up")
            ) {
              Icon(Icons.Filled.VolumeUp, contentDescription = "Volume Up", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
              onClick = { onHardwareButton("VOLUME_DOWN") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_vol_down")
            ) {
              Icon(Icons.Filled.VolumeDown, contentDescription = "Volume Down", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
              onClick = { onHardwareButton("POWER") },
              modifier = Modifier.size(32.dp).testTag("airmirror_hw_power")
            ) {
              Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power Button", tint = StatusOffline)
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Bottom Input Mapping Engine Bar (Keyboard Typing mapped to Mobile)
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Keyboard,
          contentDescription = null,
          tint = AirDroidGreen,
          modifier = Modifier.size(20.dp)
        )

        OutlinedTextField(
          value = pcKeyboardBuffer,
          onValueChange = { pcKeyboardBuffer = it },
          placeholder = { Text("PC Keyboard Input Bridge: Type here to stream keystrokes to mobile...", fontSize = 11.sp) },
          singleLine = true,
          shape = RoundedCornerShape(8.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (pcKeyboardBuffer.isNotBlank()) {
                onTypeKey(pcKeyboardBuffer)
                pcKeyboardBuffer = ""
              }
            }
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AirDroidGreen,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          ),
          modifier = Modifier.weight(1f).testTag("airmirror_pc_keyboard_input")
        )

        Button(
          onClick = {
            if (pcKeyboardBuffer.isNotBlank()) {
              onTypeKey(pcKeyboardBuffer)
              pcKeyboardBuffer = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = AirDroidGreen),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("airmirror_send_keys_button")
        ) {
          Text("Send Keystrokes", fontSize = 11.sp)
        }

        IconButton(onClick = onBackspace, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Filled.Backspace, contentDescription = "DEL", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        IconButton(onClick = onClearInput, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Filled.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Telemetry & Event Status Log
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "AirMirror Input Engine: ${airMirrorState.lastInputEvent}",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (airMirrorState.capturedScreenshots.isNotEmpty()) {
        Text(
          text = "Saved ${airMirrorState.capturedScreenshots.size} screenshots to PC",
          fontSize = 10.sp,
          color = AirDroidGreen,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }

  // Settings Dialog (Resolution / Bitrate Slider)
  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = { Text("AirMirror Stream Settings") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Resolution & Quality:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          listOf(
            "1080p (FHD 60FPS)" to 6.0f,
            "720p (HD 30FPS)" to 3.5f,
            "540p (Standard Low-Latency)" to 1.8f
          ).forEach { (res, bitrate) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onUpdateResolution(res, bitrate)
                  showSettingsDialog = false
                }
                .padding(vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(res, fontSize = 13.sp)
              Text("${bitrate} Mbps", fontSize = 11.sp, color = AirDroidGreen, fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
      }
    )
  }
}
