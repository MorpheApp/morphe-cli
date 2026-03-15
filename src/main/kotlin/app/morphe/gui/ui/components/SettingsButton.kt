/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import app.morphe.gui.LocalModeState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.repository.ConfigRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import app.morphe.gui.ui.theme.LocalThemeState

/**
 * Reusable settings button that can be placed on any screen.
 * @param allowCacheClear Whether to allow cache clearing (disable on patches screen and beyond)
 */
@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true
) {
    val themeState = LocalThemeState.current
    val modeState = LocalModeState.current
    val configRepository: ConfigRepository = koinInject()
    val scope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoCleanupTempFiles by remember { mutableStateOf(true) }

    // Load config when dialog is shown
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            val config = configRepository.loadConfig()
            autoCleanupTempFiles = config.autoCleanupTempFiles
        }
    }

    Surface(
        onClick = { showSettingsDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }

    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = themeState.current,
            onThemeChange = { themeState.onChange(it) },
            autoCleanupTempFiles = autoCleanupTempFiles,
            onAutoCleanupChange = { enabled ->
                autoCleanupTempFiles = enabled
                scope.launch {
                    configRepository.setAutoCleanupTempFiles(enabled)
                }
            },
            useExpertMode = !modeState.isSimplified,
            onExpertModeChange = { enabled ->
                modeState.onChange(!enabled)
            },
            onDismiss = { showSettingsDialog = false },
            allowCacheClear = allowCacheClear
        )
    }
}

/**
 * Top bar row that places DeviceIndicator + SettingsButton together.
 * Use this instead of standalone SettingsButton on screens.
 */
@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIndicator()
        SettingsButton(allowCacheClear = allowCacheClear)
    }
}
