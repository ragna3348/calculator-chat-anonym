package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    ELEGANT_DARK,
    OLED_BLACK,
    STEALTH_SLATE,
    CYBER_EMERALD,
    MINIMAL_LIGHT
}

data class UserSecuritySettings(
    val isBiometricEnabled: Boolean = false,
    val customPin: String = "9999",
    val mathUsername: String = "1+4", // Default formula username
    val hasSetupMathUsername: Boolean = false,
    val isStealthNotificationEnabled: Boolean = true,
    val disguiseNotificationTitle: String = "Status Sistem Optimal",
    val disguiseNotificationBody: String = "Penyimpanan dan memori telah dioptimalkan.",
    val isAutoCloudBackupEnabled: Boolean = true,
    val lastCloudBackupTimestamp: Long = 0L,
    val isQuickPanicShakeEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.ELEGANT_DARK,
    val userDisplayName: String = "Pengguna Anonim",
    val isOfflineModeOnly: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSecuritySettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSecuritySettings {
        val themeModeStr = prefs.getString("theme_mode", AppThemeMode.ELEGANT_DARK.name) ?: AppThemeMode.ELEGANT_DARK.name
        val themeMode = try {
            AppThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            AppThemeMode.ELEGANT_DARK
        }

        val savedMathUsername = prefs.getString("math_username", null)
        val mathUsername = if (savedMathUsername.isNullOrBlank()) {
            val num1 = (1..9).random()
            val num2 = (1..9).random()
            "$num1+$num2"
        } else {
            savedMathUsername
        }

        return UserSecuritySettings(
            isBiometricEnabled = prefs.getBoolean("is_biometric_enabled", false),
            customPin = prefs.getString("custom_pin", "9999") ?: "9999",
            mathUsername = mathUsername,
            hasSetupMathUsername = prefs.getBoolean("has_setup_math_username", false),
            isStealthNotificationEnabled = prefs.getBoolean("is_stealth_notif", true),
            disguiseNotificationTitle = prefs.getString("disguise_title", "Status Sistem Optimal") ?: "Status Sistem Optimal",
            disguiseNotificationBody = prefs.getString("disguise_body", "Penyimpanan dan memori telah dioptimalkan.") ?: "Penyimpanan dan memori telah dioptimalkan.",
            isAutoCloudBackupEnabled = prefs.getBoolean("auto_cloud_backup", true),
            lastCloudBackupTimestamp = prefs.getLong("last_cloud_backup", System.currentTimeMillis() - 1000 * 60 * 30),
            isQuickPanicShakeEnabled = prefs.getBoolean("quick_panic", true),
            themeMode = themeMode,
            userDisplayName = prefs.getString("display_name", "Pengguna Anonim") ?: "Pengguna Anonim",
            isOfflineModeOnly = prefs.getBoolean("offline_mode_only", false)
        )
    }

    fun updateSettings(transform: (UserSecuritySettings) -> UserSecuritySettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit().apply {
            putBoolean("is_biometric_enabled", updated.isBiometricEnabled)
            putString("custom_pin", updated.customPin)
            putString("math_username", updated.mathUsername)
            putBoolean("has_setup_math_username", updated.hasSetupMathUsername)
            putBoolean("is_stealth_notif", updated.isStealthNotificationEnabled)
            putString("disguise_title", updated.disguiseNotificationTitle)
            putString("disguise_body", updated.disguiseNotificationBody)
            putBoolean("auto_cloud_backup", updated.isAutoCloudBackupEnabled)
            putLong("last_cloud_backup", updated.lastCloudBackupTimestamp)
            putBoolean("quick_panic", updated.isQuickPanicShakeEnabled)
            putString("theme_mode", updated.themeMode.name)
            putString("display_name", updated.userDisplayName)
            putBoolean("offline_mode_only", updated.isOfflineModeOnly)
            apply()
        }
    }
}
