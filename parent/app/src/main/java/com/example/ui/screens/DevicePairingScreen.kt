package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun DevicePairingScreen(
  pairingCode: String,
  onRefreshCode: () -> Unit,
  onPairDevice: (code: String, name: String, ip: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var manualCodeInput by remember { mutableStateOf("") }
  var deviceNameInput by remember { mutableStateOf("") }
  var ipAddressInput by remember { mutableStateOf("") }
  var pairedSuccessNotice by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Header
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(AirDroidGreen.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.QrCodeScanner,
          contentDescription = null,
          tint = AirDroidGreen,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Bind & Pair Child Device",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "Scan QR code or enter the 6-digit key in the AirDroid Child App",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Dynamic QR Code & 6-Digit Alphanumeric Card
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier
        .widthIn(max = 440.dp)
        .testTag("device_pairing_card")
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // QR Code Canvas graphic
        Box(
          modifier = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / 15f
            // Outer corner patterns for QR code
            fun drawCorner(x: Float, y: Float) {
              drawRect(Color.Black, Offset(x, y), Size(step * 4, step * 4))
              drawRect(Color.White, Offset(x + step, y + step), Size(step * 2, step * 2))
              drawRect(Color.Black, Offset(x + step * 1.3f, y + step * 1.3f), Size(step * 1.4f, step * 1.4f))
            }
            drawCorner(0f, 0f)
            drawCorner(size.width - step * 4, 0f)
            drawCorner(0f, size.height - step * 4)

            // Random pseudo QR matrix pixels
            val hash = pairingCode.hashCode()
            for (i in 0..14) {
              for (j in 0..14) {
                if ((i < 5 && j < 5) || (i > 9 && j < 5) || (i < 5 && j > 9)) continue
                if ((hash xor (i * 31 + j * 17)) % 3 == 0) {
                  drawRect(Color.Black, Offset(i * step, j * step), Size(step * 0.9f, step * 0.9f))
                }
              }
            }

            // AirDroid Center Logo badge
            drawCircle(AirDroidGreen, radius = step * 1.6f, center = Offset(size.width / 2f, size.height / 2f))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Alphanumeric 6-Digit Pairing Key
        Text("OR ENTER 6-DIGIT CODE:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surface,
          border = androidx.compose.foundation.BorderStroke(1.dp, AirDroidGreen.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = pairingCode,
              fontSize = 20.sp,
              fontWeight = FontWeight.Black,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 2.sp,
              color = AirDroidGreen
            )
            IconButton(onClick = onRefreshCode, modifier = Modifier.size(24.dp)) {
              Icon(Icons.Filled.Refresh, contentDescription = "Refresh Code", tint = AirDroidGreen, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }

    // Manual Pairing Form
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.widthIn(max = 440.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Manual Device Pair",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
          value = deviceNameInput,
          onValueChange = { deviceNameInput = it },
          label = { Text("Device Name (e.g., Sarah's Galaxy S24)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pair_device_name_input")
        )

        OutlinedTextField(
          value = ipAddressInput,
          onValueChange = { ipAddressInput = it },
          label = { Text("Child IP (e.g. 192.168.1.42)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = manualCodeInput,
          onValueChange = { manualCodeInput = it },
          label = { Text("Pairing Key (e.g. AD-849204)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pair_code_input")
        )

        Button(
          onClick = {
            val code = manualCodeInput.ifBlank { pairingCode }
            val name = deviceNameInput.ifBlank { "Child Android Phone" }
            onPairDevice(code, name, ipAddressInput.trim())
            pairedSuccessNotice = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = AirDroidGreen),
          modifier = Modifier.fillMaxWidth().testTag("confirm_pair_button")
        ) {
          Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Confirm Pairing & Connect", fontSize = 13.sp)
        }

        if (pairedSuccessNotice) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = AirDroidGreen.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "✓ Device successfully paired and added to workspace!",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = AirDroidGreen,
              modifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    }

    // Local Subnet Discovery Card
    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.widthIn(max = 440.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AirDroidCyan.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Filled.WifiFind, contentDescription = null, tint = AirDroidCyan, modifier = Modifier.size(20.dp))
        }
        Column {
          Text(
            text = "Local Wi-Fi Discovery Active",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Enter the Child device IP above. Both devices must be on the same Wi-Fi network.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
