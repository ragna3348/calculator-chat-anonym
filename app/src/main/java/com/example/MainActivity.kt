package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.repository.AppRepository
import com.example.ui.calculator.CalculatorScreen
import com.example.ui.chat.ChatConversationScreen
import com.example.ui.chat.ChatListScreen
import com.example.ui.components.BiometricPinUnlockDialog
import com.example.ui.components.MathUsernameSetupDialog
import com.example.ui.theme.CalculatorVaultTheme
import com.example.ui.vault.CloudBackupSyncScreen
import com.example.ui.vault.SecurityAuditScreen
import com.example.ui.vault.StealthSettingsScreen
import com.example.utils.NotificationHelper
import com.example.utils.SettingsManager
import com.example.viewmodel.CalculatorEvent
import com.example.viewmodel.CalculatorViewModel
import com.example.viewmodel.VaultScreen
import com.example.viewmodel.VaultToastEvent
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppDisplayMode {
    CALCULATOR,
    VAULT
}

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var settingsManager: SettingsManager
    private lateinit var repository: AppRepository
    private lateinit var calculatorViewModel: CalculatorViewModel
    private lateinit var vaultViewModel: VaultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)

        database = AppDatabase.getInstance(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        repository = AppRepository(applicationContext, database.appDao(), settingsManager)

        calculatorViewModel = CalculatorViewModel(repository)
        vaultViewModel = VaultViewModel(applicationContext, repository, settingsManager)

        setContent {
            val settings by settingsManager.settings.collectAsStateWithLifecycle()
            val vaultScreen by vaultViewModel.currentScreen.collectAsStateWithLifecycle()
            val existingMathUsernames by vaultViewModel.existingMathUsernames.collectAsStateWithLifecycle()
            val showMathSetupDialog by vaultViewModel.showMathSetupDialog.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            var appMode by remember { mutableStateOf(AppDisplayMode.CALCULATOR) }
            var showAuthDialog by remember { mutableStateOf(false) }

            // Listen for Calculator secret unlock
            LaunchedEffect(Unit) {
                calculatorViewModel.events.collectLatest { event ->
                    when (event) {
                        is CalculatorEvent.OpenSecretVault -> {
                            if (settings.isBiometricEnabled) {
                                showAuthDialog = true
                            } else {
                                appMode = AppDisplayMode.VAULT
                                Toast.makeText(applicationContext, "Brankas Rahasia Terbuka", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is CalculatorEvent.ShowToast -> {
                            Toast.makeText(applicationContext, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Listen for Vault toast / snackbar events
            LaunchedEffect(Unit) {
                vaultViewModel.toastEvents.collectLatest { toastEvent ->
                    when (toastEvent) {
                        is VaultToastEvent.Success -> {
                            snackbarHostState.showSnackbar(toastEvent.message)
                        }
                        is VaultToastEvent.Error -> {
                            snackbarHostState.showSnackbar(toastEvent.message)
                        }
                        is VaultToastEvent.Info -> {
                            snackbarHostState.showSnackbar(toastEvent.message)
                        }
                    }
                }
            }

            CalculatorVaultTheme(themeMode = settings.themeMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = appMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        label = "AppModeTransition"
                    ) { targetMode ->
                        when (targetMode) {
                            AppDisplayMode.CALCULATOR -> {
                                CalculatorScreen(
                                    viewModel = calculatorViewModel,
                                    userMathUsername = settings.mathUsername,
                                    onOpenVaultDirect = {
                                        if (settings.isBiometricEnabled) {
                                            showAuthDialog = true
                                        } else {
                                            appMode = AppDisplayMode.VAULT
                                        }
                                    },
                                    onOpenMathSetup = {
                                        vaultViewModel.openMathSetupDialog()
                                    }
                                )
                            }
                            AppDisplayMode.VAULT -> {
                                BackHandler {
                                    when (vaultScreen) {
                                        VaultScreen.CONVERSATION -> vaultViewModel.closeConversation()
                                        VaultScreen.CLOUD_SYNC,
                                        VaultScreen.STEALTH_SETTINGS -> vaultViewModel.navigateTo(VaultScreen.CHATS)
                                        VaultScreen.SECURITY_AUDIT -> vaultViewModel.navigateTo(VaultScreen.STEALTH_SETTINGS)
                                        VaultScreen.CHATS -> appMode = AppDisplayMode.CALCULATOR
                                    }
                                }

                                when (vaultScreen) {
                                    VaultScreen.CHATS -> {
                                        ChatListScreen(
                                            viewModel = vaultViewModel,
                                            onLockToCalculator = {
                                                appMode = AppDisplayMode.CALCULATOR
                                                calculatorViewModel.onClear()
                                            }
                                        )
                                    }
                                    VaultScreen.CONVERSATION -> {
                                        ChatConversationScreen(
                                            viewModel = vaultViewModel
                                        )
                                    }
                                    VaultScreen.CLOUD_SYNC -> {
                                        CloudBackupSyncScreen(
                                            viewModel = vaultViewModel
                                        )
                                    }
                                    VaultScreen.STEALTH_SETTINGS -> {
                                        StealthSettingsScreen(
                                            viewModel = vaultViewModel
                                        )
                                    }
                                    VaultScreen.SECURITY_AUDIT -> {
                                        SecurityAuditScreen(
                                            viewModel = vaultViewModel
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Biometric & PIN Unlock Dialog
                    if (showAuthDialog) {
                        BiometricPinUnlockDialog(
                            correctPin = settings.customPin,
                            onUnlocked = {
                                showAuthDialog = false
                                appMode = AppDisplayMode.VAULT
                                Toast.makeText(applicationContext, "Autentikasi Berhasil", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = {
                                showAuthDialog = false
                            }
                        )
                    }

                    // First-Time Setup or Manual Math Username Dialog
                    if (!settings.hasSetupMathUsername || showMathSetupDialog) {
                        MathUsernameSetupDialog(
                            currentUsername = settings.mathUsername,
                            existingUsernames = existingMathUsernames,
                            currentDisplayName = settings.userDisplayName,
                            canDismiss = settings.hasSetupMathUsername,
                            onUsernameSelected = { mathUsername, displayName ->
                                vaultViewModel.setMathUsername(mathUsername, displayName)
                            },
                            onDismiss = {
                                vaultViewModel.dismissMathSetupDialog()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::vaultViewModel.isInitialized) {
            vaultViewModel.setUserPresence(true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::vaultViewModel.isInitialized) {
            vaultViewModel.setUserPresence(false)
        }
    }
}
