package app.morphe.gui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.di.appModule
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import app.morphe.gui.ui.screens.home.HomeScreen
import app.morphe.gui.ui.screens.quick.QuickPatchContent
import app.morphe.gui.ui.screens.quick.QuickPatchViewModel
import app.morphe.gui.util.PatchService
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeState
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.Logger

/**
 * Mode state for switching between simplified and full mode.
 */
data class ModeState(
    val isSimplified: Boolean,
    val onChange: (Boolean) -> Unit
)

val LocalModeState = staticCompositionLocalOf<ModeState> {
    error("No ModeState provided")
}

@Composable
fun App(initialSimplifiedMode: Boolean = true) {
    LaunchedEffect(Unit) {
        Logger.init()
    }

    KoinApplication(application = {
        modules(appModule)
    }) {
        AppContent(initialSimplifiedMode)
    }
}

@Composable
private fun AppContent(initialSimplifiedMode: Boolean) {
    val configRepository: ConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()

    var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
    var isSimplifiedMode by remember { mutableStateOf(initialSimplifiedMode) }
    var isLoading by remember { mutableStateOf(true) }

    // Initialize PatchSourceManager and load config on startup
    LaunchedEffect(Unit) {
        patchSourceManager.initialize()
        val config = configRepository.loadConfig()
        themePreference = config.getThemePreference()
        isSimplifiedMode = config.useSimplifiedMode
        isLoading = false
    }

    // Callback for changing theme
    val onThemeChange: (ThemePreference) -> Unit = { newTheme ->
        themePreference = newTheme
        scope.launch {
            configRepository.setThemePreference(newTheme)
            Logger.info("Theme changed to: ${newTheme.name}")
        }
    }

    // Callback for changing mode
    val onModeChange: (Boolean) -> Unit = { simplified ->
        isSimplifiedMode = simplified
        scope.launch {
            configRepository.setUseSimplifiedMode(simplified)
            Logger.info("Mode changed to: ${if (simplified) "Simplified" else "Full"}")
        }
    }

    val themeState = ThemeState(
        current = themePreference,
        onChange = onThemeChange
    )

    val modeState = ModeState(
        isSimplified = isSimplifiedMode,
        onChange = onModeChange
    )

    // Start/stop DeviceMonitor with app lifecycle
    DisposableEffect(Unit) {
        DeviceMonitor.startMonitoring()
        onDispose {
            DeviceMonitor.stopMonitoring()
        }
    }

    MorpheTheme(themePreference = themePreference) {
        CompositionLocalProvider(
            LocalThemeState provides themeState,
            LocalModeState provides modeState
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                if (!isLoading) {
                    // ViewModels observe PatchSourceManager.sourceVersion internally
                    // and reload when the active source changes — no Navigator recreation needed.
                    val patchService: PatchService = koinInject()
                    val quickViewModel = remember {
                        QuickPatchViewModel(patchSourceManager, patchService, configRepository)
                    }

                    Crossfade(targetState = isSimplifiedMode) { simplified ->
                        if (simplified) {
                            QuickPatchContent(quickViewModel)
                        } else {
                            Navigator(HomeScreen()) { navigator ->
                                SlideTransition(navigator)
                            }
                        }
                    }
                }
            }
        }
    }
}
