package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppDestination
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalculatorCamouflageScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.ParentSimulatorScreen
import com.example.ui.screens.PermissionWizardScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                AirDroidChildAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions whenever returning from system settings
        AirDroidChildApp.touchEngine?.let {
            // Can be observed
        }
    }
}

@Composable
fun AirDroidChildAppContent(viewModel: MainViewModel) {
    val destination by viewModel.currentDestination.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val activeDevice by viewModel.activeDevice.collectAsState()
    val daemonStatus by viewModel.daemonStatus.collectAsState()
    val connectedClients by viewModel.connectedClientsCount.collectAsState()
    val touchesExecuted by viewModel.touchesExecuted.collectAsState()
    val notificationsSynced by viewModel.notificationsSyncedCount.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val recentNotifications by viewModel.recentNotifications.collectAsState()
    val pairingCode by viewModel.pairingCode.collectAsState()
    val pairingStatusMessage by viewModel.pairingStatusMessage.collectAsState()

    val localIp = AirDroidChildApp.daemonServer?.getLocalIpAddress() ?: "127.0.0.1"

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (destination) {
                is AppDestination.Splash -> {
                    SplashScreen(
                        onTimeout = { nextDest ->
                            val stealth = AirDroidChildApp.stealthManager
                            if (stealth?.isCamouflageEnabled?.value == true) {
                                viewModel.navigateTo(AppDestination.CalculatorCamouflage)
                            } else {
                                viewModel.navigateTo(nextDest)
                            }
                        },
                        isPaired = pairedDevices.isNotEmpty(),
                        allPermissionsGranted = permissionStatus.allEssentialGranted
                    )
                }
                is AppDestination.Auth -> {
                    AuthScreen(
                        onLoginSuccess = { email ->
                            viewModel.loginWithAccount(email)
                        },
                        onSkipToLocalMode = {
                            viewModel.skipSignInLocalMode()
                        },
                        onScanQuickCode = {
                            viewModel.navigateTo(AppDestination.Pairing)
                        }
                    )
                }
                is AppDestination.PermissionWizard -> {
                    BackHandler {
                        viewModel.navigateTo(AppDestination.Auth)
                    }
                    PermissionWizardScreen(
                        status = permissionStatus,
                        onRefresh = { viewModel.checkPermissions() },
                        onRememberAndFinish = { viewModel.markSetupCompletedAndRemember() },
                        onToggleAutoClicker = { viewModel.toggleAutoClicker(it) },
                        onProceed = {
                            if (pairedDevices.isEmpty()) {
                                viewModel.navigateTo(AppDestination.Pairing)
                            } else {
                                viewModel.navigateTo(AppDestination.Dashboard)
                            }
                        }
                    )
                }
                is AppDestination.Pairing -> {
                    BackHandler {
                        viewModel.navigateTo(AppDestination.Auth)
                    }
                    PairingScreen(
                        childPairingCode = pairingCode,
                        localIp = localIp,
                        statusMessage = pairingStatusMessage,
                        onPairWithCode = { code ->
                            viewModel.pairWithCode(code)
                        },
                        onPairWithQr = { qrToken ->
                            viewModel.pairViaQrCode(qrToken)
                        },
                        onBack = {
                            viewModel.navigateTo(AppDestination.Auth)
                        }
                    )
                }
                is AppDestination.Dashboard -> {
                    DashboardScreen(
                        daemonStatus = daemonStatus,
                        activeDevice = activeDevice ?: pairedDevices.firstOrNull(),
                        connectedClients = connectedClients,
                        touchesExecuted = touchesExecuted,
                        notificationsSynced = notificationsSynced,
                        recentLogs = recentLogs,
                        onToggleDaemon = { viewModel.toggleDaemonService() },
                        onPauseResume = { viewModel.pauseOrResumeDaemon() },
                        onOpenParentSimulator = { viewModel.navigateTo(AppDestination.ParentSimulator) },
                        onOpenCamouflageCalculator = { viewModel.navigateTo(AppDestination.CalculatorCamouflage) },
                        onOpenWizard = { viewModel.navigateTo(AppDestination.PermissionWizard) },
                        onClearLogs = { viewModel.clearAuditLogs() }
                    )
                }
                is AppDestination.ParentSimulator -> {
                    BackHandler {
                        viewModel.navigateTo(AppDestination.Dashboard)
                    }
                    ParentSimulatorScreen(
                        notifications = recentNotifications,
                        onSendQuickReply = { key, reply ->
                            viewModel.sendQuickReply(key, reply)
                        },
                        onBack = {
                            viewModel.navigateTo(AppDestination.Dashboard)
                        }
                    )
                }
                is AppDestination.CalculatorCamouflage -> {
                    CalculatorCamouflageScreen(
                        onUnlockSuccess = {
                            viewModel.navigateTo(AppDestination.Dashboard)
                        }
                    )
                }
            }
        }
    }
}
