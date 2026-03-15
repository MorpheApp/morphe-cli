/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.di

import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.util.PatchService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.ui.screens.patches.PatchesViewModel
import app.morphe.gui.ui.screens.patches.PatchSelectionViewModel
import app.morphe.gui.ui.screens.patching.PatchingScreenModel

/**
 * Main Koin module for dependency injection.
 */
val appModule = module {

    // JSON serialization
    single {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    // Ktor HTTP Client
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(get())
            }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        app.morphe.gui.util.Logger.debug("HTTP: $message")
                    }
                }
            }
            engine {
                requestTimeout = 60_000
            }
        }
    }

    // Repositories and Services
    single { ConfigRepository() }
    single { PatchRepository(get()) }
    single { PatchService() }

    // ViewModels (ScreenModels)
    factory { HomeViewModel(get(), get(), get()) }
    factory { params -> PatchesViewModel(params.get(), params.get(), get(), get()) }
    factory { params -> PatchSelectionViewModel(params.get(), params.get(), params.get(), params.get(), params.get(), get(), get()) }
    factory { params -> PatchingScreenModel(params.get(), get()) }
}
