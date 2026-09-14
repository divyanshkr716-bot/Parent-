package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.ChildDevice
import com.example.data.model.ConnectionMode
import com.example.data.model.UserSession
import com.example.ui.components.AppTopBar
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val dev = ChildDevice(
      id = "dev-pixel8",
      name = "Pixel 8 Pro (Ethan)",
      model = "Google Pixel 8 Pro",
      osVersion = "Android 14",
      batteryPercent = 84,
      isCharging = false,
      storageUsedGb = 78.4,
      storageTotalGb = 256.0,
      isOnline = true,
      wifiSsid = "Home_5G_Extender",
      wifiSignalDbm = -54,
      ipAddress = "192.168.1.142",
      connectionMode = ConnectionMode.LOCAL_P2P,
      lastSeen = "Active now"
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        AppTopBar(
          selectedDevice = dev,
          devices = listOf(dev),
          onSelectDevice = {},
          onToggleConnectionMode = {},
          latencyMs = 16,
          fps = 60,
          isDarkTheme = true,
          onToggleTheme = {},
          userSession = UserSession(),
          onOpenAuthDialog = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

