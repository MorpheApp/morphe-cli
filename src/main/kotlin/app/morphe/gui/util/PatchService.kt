package app.morphe.gui.util

import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.library.ApkUtils
import app.morphe.library.ApkUtils.applyTo
import app.morphe.library.setOptions
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.loadPatchesFromJar
import com.reandroid.apkeditor.merge.Merger
import com.reandroid.apkeditor.merge.MergerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
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
            val patches = loadPatchesFromJar(setOf(patchFile))

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
        } catch (e: Exception) {
            Logger.error("Failed to load patches", e)
            Result.failure(e)
        }
    }

    /**
     * Execute patching operation with progress callbacks.
     */
    suspend fun patch(
        patchesFilePath: String,
        inputApkPath: String,
        outputApkPath: String,
        enabledPatches: List<String> = emptyList(),
        disabledPatches: List<String> = emptyList(),
        options: Map<String, String> = emptyMap(),
        exclusiveMode: Boolean = false,
        riplibs: List<String> = emptyList(),
        onProgress: (String) -> Unit = {}
    ): Result<PatchResult> = withContext(Dispatchers.IO) {
        val tempDir = FileUtils.createPatchingTempDir()
        val tempOutputPath = File(tempDir, File(outputApkPath).name)

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

            onProgress("Loading patches...")
            val patches = loadPatchesFromJar(setOf(patchFile))

            // Handle APKM format (split APK bundle)
            var mergedApkToCleanup: File? = null
            val actualInputApk = if (inputApk.extension.equals("apkm", ignoreCase = true)) {
                onProgress("Converting APKM to APK...")
                val mergedApk = File(tempDir, "${inputApk.nameWithoutExtension}-merged.apk")
                val mergerOptions = MergerOptions().apply {
                    this.inputFile = inputApk
                    this.outputFile = mergedApk
                    cleanMeta = true
                }
                Merger(mergerOptions).run()
                mergedApkToCleanup = mergedApk
                mergedApk
            } else {
                inputApk
            }

            val patcherTempDir = File(tempDir, "patcher")
            patcherTempDir.mkdirs()

            onProgress("Initializing patcher...")
            val patcherConfig = PatcherConfig(
                actualInputApk,
                patcherTempDir,
                null, // aapt binary path
                patcherTempDir.absolutePath
            )

            val appliedPatches = mutableListOf<String>()
            val failedPatches = mutableListOf<Pair<String, String>>()

            Patcher(patcherConfig).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.packageVersion

                onProgress("Filtering patches for $packageName v$packageVersion...")

                // Filter patches based on compatibility and selection
                val filteredPatches = patches.filter { patch ->
                    val patchName = patch.name ?: return@filter false

                    // Check if explicitly disabled
                    if (patchName in disabledPatches) {
                        onProgress("Skipping disabled: $patchName")
                        return@filter false
                    }

                    // Check package compatibility
                    val isCompatible = patch.compatiblePackages?.let { packages ->
                        packages.any { (name, versions) ->
                            name == packageName && (versions?.isEmpty() != false || versions.contains(packageVersion))
                        }
                    } ?: true // Universal patches

                    if (!isCompatible) {
                        return@filter false
                    }

                    // In exclusive mode, only include explicitly enabled patches
                    if (exclusiveMode) {
                        patchName in enabledPatches
                    } else {
                        // Include if: enabled by default OR explicitly enabled
                        patch.use || patchName in enabledPatches
                    }
                }.toSet()

                onProgress("Applying ${filteredPatches.size} patches...")

                // Set patch options if any
                if (options.isNotEmpty()) {
                    val optionsMap = enabledPatches.associateWith { patchName ->
                        options.filterKeys { it.startsWith("$patchName.") }
                            .mapKeys { it.key.removePrefix("$patchName.") }
                            .mapValues { it.value as Any? }
                            .toMutableMap()
                    }.filter { it.value.isNotEmpty() }

                    if (optionsMap.isNotEmpty()) {
                        filteredPatches.setOptions(optionsMap)
                    }
                }

                patcher += filteredPatches

                // Execute patches
                runBlocking {
                    patcher().collect { patchResult ->
                        val patchName = patchResult.patch.name ?: "Unknown"
                        patchResult.exception?.let { exception ->
                            val error = StringWriter().use { writer ->
                                exception.printStackTrace(PrintWriter(writer))
                                writer.toString()
                            }
                            onProgress("FAILED: $patchName")
                            Logger.error("Patch failed: $patchName\n$error")
                            failedPatches.add(patchName to error)
                        } ?: run {
                            onProgress("Applied: $patchName")
                            Logger.info("Patch applied: $patchName")
                            appliedPatches.add(patchName)
                        }
                    }
                }

                // Get patcher result
                val patcherResult = patcher.get()

                onProgress("Rebuilding APK...")
                val rebuiltApk = File(tempDir, "rebuilt.apk")
                actualInputApk.copyTo(rebuiltApk, overwrite = true)
                patcherResult.applyTo(rebuiltApk)

                if (riplibs.isNotEmpty()) {
                    onProgress("Stripping native libraries...")
                    ApkLibraryStripper.stripLibraries(rebuiltApk, riplibs) { onProgress(it) }
                }

                onProgress("Signing APK...")
                val keystorePath = File(tempDir, "morphe.keystore")
                ApkUtils.signApk(
                    rebuiltApk,
                    tempOutputPath,
                    "Morphe",
                    ApkUtils.KeyStoreDetails(
                        keystorePath,
                        null, // password
                        "Morphe Key",
                        "" // entry password
                    )
                )

                // Move to final location
                outputFile.parentFile?.mkdirs()
                tempOutputPath.copyTo(outputFile, overwrite = true)

                onProgress("Patching complete!")
                Logger.info("Patched APK saved to: ${outputFile.absolutePath}")

                // Cleanup merged APK if created
                mergedApkToCleanup?.delete()
            }

            Result.success(PatchResult(
                success = failedPatches.isEmpty(),
                outputPath = outputFile.absolutePath,
                appliedPatches = appliedPatches,
                failedPatches = failedPatches.map { it.first }
            ))
        } catch (e: Exception) {
            Logger.error("Patching failed", e)
            Result.failure(e)
        } finally {
            // Cleanup temp directory
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                Logger.warn("Failed to cleanup temp directory: ${e.message}")
            }
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
                    type = mapKTypeToOptionType(opt.type),
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
    private fun mapKTypeToOptionType(kType: KType): PatchOptionType {
        val typeName = kType.toString()
        return when {
            typeName.contains("Boolean") -> PatchOptionType.BOOLEAN
            typeName.contains("Int") -> PatchOptionType.INT
            typeName.contains("Long") -> PatchOptionType.LONG
            typeName.contains("Float") || typeName.contains("Double") -> PatchOptionType.FLOAT
            typeName.contains("List") || typeName.contains("Array") || typeName.contains("Set") -> PatchOptionType.LIST
            else -> PatchOptionType.STRING
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
