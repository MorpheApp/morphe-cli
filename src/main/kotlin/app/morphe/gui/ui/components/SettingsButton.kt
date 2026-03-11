package app.morphe.gui.ui.components

import app.morphe.gui.LocalModeState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import app.morphe.gui.ui.theme.LocalThemeState

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true
) {
    val themeState = LocalThemeState.current
    val modeState = LocalModeState.current
    val configRepository: ConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoCleanupTempFiles by remember { mutableStateOf(true) }
    var patchSources by remember { mutableStateOf<List<PatchSource>>(emptyList()) }
    var activePatchSourceId by remember { mutableStateOf("") }

    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            val config = configRepository.loadConfig()
            autoCleanupTempFiles = config.autoCleanupTempFiles
            patchSources = config.patchSource
            activePatchSourceId = config.activePatchSourceId
        }
    }

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(150)
    )

    IconButton(
        onClick = { showSettingsDialog = true },
        modifier = modifier
            .size(34.dp)
            .hoverable(hoverInteraction)
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .background(Color.Transparent, RoundedCornerShape(2.dp))
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = if (isHovered) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
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
            allowCacheClear = allowCacheClear,
            patchSources = patchSources,
            activePatchSourceId = activePatchSourceId,
            onActivePatchSourceChange = { id ->
                if (id != activePatchSourceId) {
                    activePatchSourceId = id
                    scope.launch {
                        withContext(NonCancellable) {
                            patchSourceManager.switchSource(id)
                        }
                    }
                }
            },
            onAddPatchSource = { source ->
                patchSources = patchSources + source
                scope.launch {
                    configRepository.addPatchSource(source)
                }
            },
            onEditPatchSource = { updated ->
                patchSources = patchSources.map { if (it.id == updated.id) updated else it }
                scope.launch {
                    configRepository.updatePatchSource(updated)
                    if (updated.id == activePatchSourceId) {
                        patchSourceManager.clearAll()
                        patchSourceManager.switchSource(updated.id)
                    }
                }
            },
            onRemovePatchSource = { id ->
                patchSources = patchSources.filter { it.id != id }
                if (activePatchSourceId == id) {
                    activePatchSourceId = "morphe-default"
                }
                scope.launch {
                    configRepository.removePatchSource(id)
                }
            }
        )
    }
}

@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIndicator()
        SettingsButton(allowCacheClear = allowCacheClear)
    }
}
