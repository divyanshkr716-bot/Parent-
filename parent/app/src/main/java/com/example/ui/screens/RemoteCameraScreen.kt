package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChildDevice
import com.example.ui.RemoteCameraUiState
import com.example.ui.theme.*

@Composable
fun RemoteCameraScreen(
  device: ChildDevice?,
  cameraState: RemoteCameraUiState,
  latencyMs: Int,
  fps: Int,
  audioVolumeDb: Float = 0f,
  isOneWayAudioActive: Boolean = false,
  onToggleCameraFacing: () -> Unit,
  onToggleFlashlight: () -> Unit,
  onToggleAudio: () -> Unit,
  onCaptureSnapshot: () -> Unit,
  onToggleRecording: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Top Bar with surveillance status and latency
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(if (cameraState.isStreaming) StatusOnline else StatusOffline)
          )
          Text(
            text = "Remote Camera Stream • ${if (cameraState.isFrontCamera) "Front Camera (10.5 MP)" else "Rear Camera (50 MP OIS)"}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (cameraState.isFlashlightOn) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF59E0B).copy(alpha = 0.2f)) {
              Text(
                "⚡ Torch ON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }
          Text(
            text = "Stream: 1080p @ $fps FPS (${latencyMs}ms)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Viewfinder Canvas Window
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF030712))
        .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        .testTag("remote_camera_viewfinder"),
      contentAlignment = Alignment.Center
    ) {
      // Real Camera Video Feed or Error/Connecting State
      if (cameraState.currentFrame != null) {
        Image(
          bitmap = cameraState.currentFrame,
          contentDescription = "Live Remote Camera Feed",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      } else if (cameraState.permissionError != null) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = StatusWarning,
            modifier = Modifier.size(48.dp)
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = "Camera Permission Required",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 16.sp
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = cameraState.permissionError,
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = if (cameraState.isFlashlightOn)
                  listOf(Color(0xFF1E293B), Color(0xFF0B192C), Color(0xFF030712))
                else
                  listOf(Color(0xFF0F172A), Color(0xFF060D1A), Color(0xFF020408))
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(color = AirDroidGreen)
        }
      }

      // Reticle & Viewfinder Grid lines
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Viewfinder HUD
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Surface(shape = RoundedCornerShape(4.dp), color = Color(0x88000000)) {
            Text(
              text = "● REC [LIVE FEED]",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = StatusOnline,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }

          Surface(shape = RoundedCornerShape(4.dp), color = Color(0x88000000)) {
            Text(
              text = "ISO 200 • f/1.7 • 1/120s • E2EE",
              fontSize = 11.sp,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        // Center Target Crosshair
        Box(
          modifier = Modifier
            .size(100.dp)
            .border(1.dp, AirDroidGreen.copy(alpha = 0.5f), CircleShape)
            .align(Alignment.CenterHorizontally),
          contentAlignment = Alignment.Center
        ) {
          Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AirDroidGreen))
        }

        // Bottom Viewfinder Overlay: Device Location & Timestamp
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${device?.name ?: "Mobile"} • ${device?.wifiSsid ?: "Wi-Fi"}",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
          )

          if (cameraState.isRecording) {
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xCCEF4444)) {
              Text(
                text = "RECORDING 00:${cameraState.recordSeconds.toString().padStart(2, '0')}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      }
    }

    // Camera Controls Toolbar
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Switch Camera (Front / Rear)
        IconButton(
          onClick = onToggleCameraFacing,
          modifier = Modifier.size(44.dp).testTag("camera_switch_lens_button")
        ) {
          Icon(
            imageVector = Icons.Filled.FlipCameraAndroid,
            contentDescription = "Switch Camera Front/Back",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }

        // Toggle Flashlight / Torch
        IconButton(
          onClick = onToggleFlashlight,
          modifier = Modifier.size(44.dp).testTag("camera_flashlight_button")
        ) {
          Icon(
            imageVector = if (cameraState.isFlashlightOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = "Toggle Flashlight",
            tint = if (cameraState.isFlashlightOn) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }

        // Snapshot Button (Large Center Shutter)
        Button(
          onClick = onCaptureSnapshot,
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = AirDroidGreen),
          contentPadding = PaddingValues(16.dp),
          modifier = Modifier.size(54.dp).testTag("camera_shutter_snapshot_button")
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = "Take Remote Snapshot",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
          )
        }

        // Record Video Button
        IconButton(
          onClick = onToggleRecording,
          modifier = Modifier.size(44.dp).testTag("camera_record_video_button")
        ) {
          Icon(
            imageVector = if (cameraState.isRecording) Icons.Filled.StopCircle else Icons.Filled.Videocam,
            contentDescription = "Record Video Clip",
            tint = if (cameraState.isRecording) StatusOffline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }

        // One-Way Audio Monitoring Toggle
        IconButton(
          onClick = onToggleAudio,
          modifier = Modifier.size(44.dp).testTag("camera_audio_toggle_button")
        ) {
          Icon(
            imageVector = if (cameraState.isAudioOn || isOneWayAudioActive) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = "Toggle Audio Monitoring",
            tint = if (cameraState.isAudioOn || isOneWayAudioActive) AirDroidCyan else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }

    // One-Way Audio Real-time Monitoring Bar
    if (cameraState.isAudioOn || isOneWayAudioActive) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = AirDroidCyan.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Hearing,
              contentDescription = null,
              tint = AirDroidCyan,
              modifier = Modifier.size(20.dp)
            )
            Column {
              Text(
                text = "One-Way Audio Stream Active (Child Microphone)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AirDroidCyan
              )
              Text(
                text = "Listening to surroundings • AudioTrack PCM 16-bit 16kHz",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          // Live dB Visualizer Bars
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            val barHeights = listOf(
              (audioVolumeDb * 0.4f).coerceIn(4f, 22f),
              (audioVolumeDb * 0.8f).coerceIn(4f, 28f),
              (audioVolumeDb * 1.0f).coerceIn(4f, 32f),
              (audioVolumeDb * 0.6f).coerceIn(4f, 24f),
              (audioVolumeDb * 0.3f).coerceIn(4f, 18f)
            )
            barHeights.forEach { h ->
              Box(
                modifier = Modifier
                  .width(4.dp)
                  .height(h.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(AirDroidCyan)
              )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${audioVolumeDb.toInt()} dB",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = AirDroidCyan
            )
          }
        }
      }
    }

    // Saved Snapshots & Video Clips Gallery on PC
    if (cameraState.capturedPhotos.isNotEmpty()) {
      Text(
        text = "SAVED SURVEILLANCE CAPTURES ON PC (${cameraState.capturedPhotos.size})",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cameraState.capturedPhotos) { photo ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Filled.Image, contentDescription = null, tint = AirDroidGreen, modifier = Modifier.size(16.dp))
              Text(photo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
              Text("• Saved to PC", fontSize = 10.sp, color = AirDroidGreen)
            }
          }
        }
      }
    }
  }
}
