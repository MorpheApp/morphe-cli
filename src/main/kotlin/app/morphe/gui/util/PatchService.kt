/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import app.morphe.engine.PatchEngine
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.reflect.KType
import app.morphe.patcher.patch.Patch as LibraryPatch

/**
 * Bridge between GUI and morphe-patcher library.
 * Replaces CliRunner with direct library calls.
 */
class PatchService {

    /**
     * Load patches from an .mpp file and convert to GUI model.
     * Optionally filter by package name.
     */
    suspend fun listPatches(
        patchesFilePath: String,
        packageName: String? = null
    ): Result<List<Patch>> = withContext(Dispatchers.IO) {
        try {
            val patchFile = File(patchesFilePath)
            if (!patchFile.exists()) {
                return@withContext Result.failure(Exception("Patches file not found: $patchesFilePath"))
            }

            Logger.info("Loading patches from: $patchesFilePath")

            // Copy to temp file so URLClassLoader locks the copy, not the cached original.
            // On Windows, the classloader holds the file locked and prevents deletion.
            val tempCopy = File.createTempFile("morphe-patches-", ".mpp")
            try {
                patchFile.copyTo(tempCopy, overwrite = true)
                val patches = loadPatchesFromJar(setOf(tempCopy))

                // Convert library patches to GUI model
                val guiPatches = patches.map { it.toGuiPatch() }

                // Filter by package name if specified
                val filtered = if (packageName != null) {
                    guiPatches.filter { patch ->
                        patch.compatiblePackages.isEmpty() || // Universal patches
                        patch.compatiblePackages.any { it.name == packageName }
                    }
                } else {
                    guiPatches
                }

                Logger.info("Loaded ${filtered.size} patches" + (packageName?.let { " for $it" } ?: ""))
                Result.success(filtered)
            } finally {
                tempCopy.deleteOnExit()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load patches", e)
            Result.failure(e)
        }
    }

    /**
     * Execute patching operation with progress callbacks.
     * Delegates to PatchEngine for the actual pipeline.
     */
    suspend fun patch(
        patchesFilePath: String,
        inputApkPath: String,
        outputApkPath: String,
        enabledPatches: List<String> = emptyList(),
        disabledPatches: List<String> = emptyList(),
        options: Map<String, String> = emptyMap(),
        exclusiveMode: Boolean = false,
        keepArchitectures: Set<CpuArchitecture> = emptySet(),
        continueOnError: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): Result<PatchResult> = withContext(Dispatchers.IO) {
        try {
            val patchFile = File(patchesFilePath)
            val inputApk = File(inputApkPath)
            val outputFile = File(outputApkPath)

            if (!patchFile.exists()) {
                return@withContext Result.failure(Exception("Patches file not found"))
            }
            if (!inputApk.exists()) {
                return@withContext Result.failure(Exception("Input APK not found"))
            }

            // Load patches (copy to temp to avoid Windows file lock)
            onProgress("Loading patches...")
            val patchTempCopy = File.createTempFile("morphe-patches-", ".mpp")
            try {
                patchFile.copyTo(patchTempCopy, overwrite = true)
                val loadedPatches = loadPatchesFromJar(setOf(patchTempCopy))

                // Convert GUI's flat "patchName.optionKey" -> value map
                // to engine's Map<patchName, Map<optionKey, value>> format
                val patchOptions = enabledPatches.associateWith { patchName ->
                    options.filterKeys { it.startsWith("$patchName.") }
                        .mapKeys { it.key.removePrefix("$patchName.") }
                        .mapValues { it.value as Any? }
                }.filter { it.value.isNotEmpty() }

                val config = PatchEngine.Config(
                    inputApk = inputApk,
                    patches = loadedPatches,
                    outputApk = outputFile,
                    enabledPatches = enabledPatches.toSet(),
                    disabledPatches = disabledPatches.toSet(),
                    exclusiveMode = exclusiveMode,
                    forceCompatibility = true,
                    patchOptions = patchOptions,
                    architecturesToKeep = keepArchitectures,
                    failOnError = !continueOnError,
                )

                val engineResult = PatchEngine.patch(config, onProgress)

                Result.success(PatchResult(
                    success = engineResult.success,
                    outputPath = engineResult.outputPath,
                    appliedPatches = engineResult.appliedPatches,
                    failedPatches = engineResult.failedPatches.map { it.name },
                ))
            } finally {
                patchTempCopy.delete()
            }
        } catch (e: Exception) {
            Logger.error("Patching failed", e)
            Result.failure(e)
        }
    }

    /**
     * Convert library Patch to GUI Patch model.
     */
    private fun LibraryPatch<*>.toGuiPatch(): Patch {
        return Patch(
            name = this.name ?: "Unknown",
            description = this.description ?: "",
            compatiblePackages = this.compatiblePackages?.map { (name, versions) ->
                CompatiblePackage(
                    name = name,
                    versions = versions?.toList() ?: emptyList()
                )
            } ?: emptyList(),
            options = this.options.values.map { opt ->
                PatchOption(
                    key = opt.key,
                    title = opt.title ?: opt.key,
                    description = opt.description ?: "",
                    type = mapKTypeToOptionType(opt.type, opt.key, opt.title ?: opt.key),
                    default = opt.default?.toString(),
                    required = opt.required
                )
            },
            isEnabled = this.use
        )
    }

    /**
     * Map Kotlin KType to GUI PatchOptionType.
     */
    private fun mapKTypeToOptionType(kType: KType, key: String, title: String): PatchOptionType {
        val typeName = kType.toString()
        return when {
            typeName.contains("Boolean") -> PatchOptionType.BOOLEAN
            typeName.contains("Int") -> PatchOptionType.INT
            typeName.contains("Long") -> PatchOptionType.LONG
            typeName.contains("Float") || typeName.contains("Double") -> PatchOptionType.FLOAT
            typeName.contains("List") || typeName.contains("Array") || typeName.contains("Set") -> PatchOptionType.LIST
            typeName.contains("File") || typeName.contains("Path") || typeName.contains("InputStream") -> PatchOptionType.FILE
            else -> {
                val combined = "$key $title".lowercase()
                val fileKeywords = listOf("icon", "image", "logo", "banner", "path", "file", "png", "jpg")
                if (fileKeywords.any { it in combined }) PatchOptionType.FILE else PatchOptionType.STRING
            }
        }
    }
}

/**
 * Result of a patching operation.
 */
data class PatchResult(
    val success: Boolean,
    val outputPath: String,
    val appliedPatches: List<String>,
    val failedPatches: List<String>
)
