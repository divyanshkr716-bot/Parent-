package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserSession
import com.example.ui.theme.*

@Composable
fun AuthSessionDialog(
  session: UserSession,
  onDismiss: () -> Unit,
  onLogin: (email: String, name: String) -> Unit
) {
  var selectedTab by remember { mutableStateOf(0) } // 0: Session Info, 1: Switch / Multi-Login
  var emailInput by remember { mutableStateOf(session.email) }
  var passwordInput by remember { mutableStateOf("") }
  var phoneInput by remember { mutableStateOf("+1 (555) 019-2831") }
  var timeoutMinutes by remember { mutableStateOf(30) }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .testTag("auth_session_dialog")
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AirDroidGreen),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
            Text(
              text = "Account & Security",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = AirDroidGreen,
          modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Session & Security", fontSize = 12.sp) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Multi-Method Login", fontSize = 12.sp) }
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
          // Current Session Details
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AirDroidCyan),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White
                  )
                }
                Column {
                  Text(
                    text = session.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = session.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Divider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Subscription:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(session.accountType, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AirDroidGreen)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Encryption Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("TLS 1.3 + E2EE Active", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StatusOnline)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Auto-Timeout settings
          Text(
            text = "Shared Computer Auto-Timeout",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            text = "Automatically terminate session if inactive on desktop/web client:",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(15, 30, 60).forEach { mins ->
              FilterChip(
                selected = timeoutMinutes == mins,
                onClick = { timeoutMinutes = mins },
                label = { Text("$mins mins", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = AirDroidGreen.copy(alpha = 0.2f),
                  selectedLabelColor = AirDroidGreen
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = AirDroidGreen),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Save Security Settings")
          }
        } else {
          // Multi-Method Login
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Social SSO Buttons
            Button(
              onClick = {
                onLogin("google.parent@gmail.com", "Google SSO User")
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
              ),
              modifier = Modifier.fillMaxWidth().testTag("auth_google_sso")
            ) {
              Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Continue with Google", fontSize = 13.sp)
            }

            Button(
              onClick = {
                onLogin("parent@icloud.com", "Apple ID User")
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
              ),
              modifier = Modifier.fillMaxWidth().testTag("auth_apple_sso")
            ) {
              Icon(Icons.Filled.PhoneIphone, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign in with Apple ID", fontSize = 13.sp)
            }

            Divider(
              modifier = Modifier.padding(vertical = 4.dp),
              color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            // Email Login
            OutlinedTextField(
              value = emailInput,
              onValueChange = { emailInput = it },
              label = { Text("Email Address", fontSize = 12.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              label = { Text("Password", fontSize = 12.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            Button(
              onClick = {
                if (emailInput.isNotBlank()) {
                  onLogin(emailInput, emailInput.substringBefore("@").replaceFirstChar { it.uppercase() })
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = AirDroidGreen),
              modifier = Modifier.fillMaxWidth().testTag("auth_email_login_button")
            ) {
              Text("Sign In with Email / Phone")
            }
          }
        }
      }
    }
  }
}
