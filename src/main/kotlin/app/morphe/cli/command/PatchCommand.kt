package app.morphe.cli.command

import app.morphe.cli.command.model.FailedPatch
import app.morphe.cli.command.model.PatchBundle
import app.morphe.cli.command.model.PatchingResult
import app.morphe.cli.command.model.PatchingStep
import app.morphe.cli.command.model.addStepResult
import app.morphe.cli.command.model.deserializeOptionValue
import app.morphe.cli.command.model.findMatchingBundle
import app.morphe.cli.command.model.mergeWith
import app.morphe.cli.command.model.toPatchBundle
import app.morphe.cli.command.model.toSerializablePatch
import app.morphe.cli.command.model.withUpdatedBundle
import app.morphe.gui.util.ApkLibraryStripper
import app.morphe.library.ApkUtils
import app.morphe.library.ApkUtils.applyTo
import app.morphe.library.installation.installer.*
import app.morphe.library.setOptions
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import com.reandroid.apkeditor.merge.Merger
import com.reandroid.apkeditor.merge.MergerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Callable
import java.util.logging.Logger

@OptIn(ExperimentalSerializationApi::class)
@CommandLine.Command(
    name = "patch",
    description = ["Patch an APK file."],
)
internal object PatchCommand : Callable<Int> {

    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    private val logger = Logger.getLogger(this::class.java.name)

    @Spec
    private lateinit var spec: CommandSpec

    @ArgGroup(exclusive = false, multiplicity = "0..*")
    private var selection = mutableSetOf<Selection>()

    internal class Selection {
        @ArgGroup(exclusive = false)
        internal var enabled: EnableSelection? = null

        internal class EnableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: EnableSelector

            internal class EnableSelector {
                @CommandLine.Option(
                    names = ["-e", "--enable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--ei"],
                    description = ["Index of the patch in the combined list of the supplied MPP files."],
                    required = true,
                )
                internal var index: Int? = null
            }

            @CommandLine.Option(
                names = ["-O", "--options"],
                description = ["Option values keyed by option keys."],
                mapFallbackValue = CommandLine.Option.NULL_VALUE,
                converter = [OptionKeyConverter::class, OptionValueConverter::class],
            )
            internal var options = mutableMapOf<String, Any?>()
        }

        @ArgGroup(exclusive = false)
        internal var disable: DisableSelection? = null

        internal class DisableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: DisableSelector

            internal class DisableSelector {
                @CommandLine.Option(
                    names = ["-d", "--disable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--di"],
                    description = ["Index of the patch in the combined list of the supplied MPP files."],
                    required = true,
                )
                internal var index: Int? = null
            }
        }
    }

    @CommandLine.Option(
        names = ["--exclusive"],
        description = ["Disable all patches except the ones enabled."],
        showDefaultValue = ALWAYS,
    )
    private var exclusive = false

    @CommandLine.Option(
        names = ["-f", "--force"],
        description = ["Don't check for compatibility with the supplied APK's version."],
        showDefaultValue = ALWAYS,
    )
    private var force: Boolean = false

    private var outputFilePath: File? = null

    @CommandLine.Option(
        names = ["-o", "--out"],
        description = ["Path to save the patched APK file to. Defaults to the same path as the supplied APK file."],
    )
    @Suppress("unused")
    private fun setOutputFilePath(outputFilePath: File?) {
        this.outputFilePath = outputFilePath?.absoluteFile
    }

    private var patchingResultOutputFilePath: File? = null

    @CommandLine.Option(
        names = ["-r", "--result-file"],
        description = ["Path to save the patching result file to"],
    )
    @Suppress("unused")
    private fun setPatchingResultOutputFilePath(outputFilePath: File?) {
        this.patchingResultOutputFilePath = outputFilePath?.absoluteFile
    }

    @CommandLine.Option(
        names = ["-i", "--install"],
        description = ["Serial of the ADB device to install to. If not specified, the first connected device will be used."],
        // Empty string to indicate that the first connected device should be used.
        fallbackValue = "",
        arity = "0..1",
    )
    private var deviceSerial: String? = null

    @CommandLine.Option(
        names = ["--mount"],
        description = ["Install the patched APK file by mounting."],
        showDefaultValue = ALWAYS,
    )
    private var mount: Boolean = false

    @CommandLine.Option(
        names = ["--keystore"],
        description = [
            "Path to the keystore file containing a private key and certificate pair to sign the patched APK file with. " +
                "Defaults to the same directory as the supplied APK file.",
        ],
    )
    private var keyStoreFilePath: File? = null

    @CommandLine.Option(
        names = ["--keystore-password"],
        description = ["Password of the keystore. Empty password by default."],
    )
    private var keyStorePassword: String? = null // Empty password by default

    @CommandLine.Option(
        names = ["--keystore-entry-alias"],
        description = ["Alias of the private key and certificate pair keystore entry."],
        showDefaultValue = ALWAYS,
    )
    private var keyStoreEntryAlias = "Morphe Key"

    @CommandLine.Option(
        names = ["--keystore-entry-password"],
        description = ["Password of the keystore entry."],
    )
    private var keyStoreEntryPassword = "" // Empty password by default

    @CommandLine.Option(
        names = ["--signer"],
        description = ["The name of the signer to sign the patched APK file with."],
        showDefaultValue = ALWAYS,
    )
    private var signer = "Morphe"

    @CommandLine.Option(
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    private var aaptBinaryPath: File? = null

    @CommandLine.Option(
        names = ["--purge"],
        description = ["Purge temporary files directory after patching."],
        showDefaultValue = ALWAYS,
    )
    private var purge: Boolean = false

    @CommandLine.Parameters(
        description = ["APK file to patch."],
        arity = "1",
    )
    @Suppress("unused")
    private fun setApk(apk: File) {
        if (!apk.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "APK file ${apk.path} does not exist",
            )
        }
        this.apk = apk
    }

    private lateinit var apk: File

    @CommandLine.Option(
        names = ["-p", "--patches"],
        description = ["One or more path to MPP files."],
        required = true,
    )
    @Suppress("unused")
    private fun setPatchesFile(patchesFiles: Set<File>) {
        patchesFiles.firstOrNull { !it.exists() }?.let {
            throw CommandLine.ParameterException(spec.commandLine(), "${it.name} can't be found")
        }
        this.patchesFiles = patchesFiles
    }

    private var patchesFiles = emptySet<File>()

    @CommandLine.Option(
        names = ["--custom-aapt2-binary"],
        description = ["Path to a custom AAPT binary to compile resources with."],
    )
    @Suppress("unused")
    private fun setAaptBinaryPath(aaptBinaryPath: File) {
        if (!aaptBinaryPath.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "AAPT binary ${aaptBinaryPath.name} does not exist",
            )
        }
        this.aaptBinaryPath = aaptBinaryPath
    }

    @CommandLine.Option(
        names = ["--unsigned"],
        description = ["Disable signing of the final apk."],
    )
    private var unsigned: Boolean = false

    @CommandLine.Option(
        names = ["--striplibs"],
        description = ["Architectures to keep, comma-separated (e.g. arm64-v8a,x86). Strips all other native architectures."],
        split = ",",
    )
    private var striplibs: List<String> = emptyList()

    @CommandLine.Option(
        names = ["--continue-on-error"],
        description = ["Continue patching even if a patch fails. By default, patching stops on the first error."],
        showDefaultValue = ALWAYS,
    )
    private var continueOnError: Boolean = false

    @CommandLine.Option(
        names = ["--options-file"],
        description = ["Path to an options JSON file to read patch enable/disable and option values from."],
    )
    @Suppress("unused")
    private fun setOptionsFilePath(optionsFilePath: File?) {
        this.optionsFilePath = optionsFilePath
    }

    private var optionsFilePath: File? = null

    @CommandLine.Option(
        names = ["--options-update"],
        description = ["Auto-update the options JSON file after patching to reflect the current patches. Without this flag, the file is left unchanged."],
        showDefaultValue = ALWAYS,
    )
    private var updateOptions: Boolean = false

    override fun call(): Int {
        // region Setup

        val outputFilePath =
            outputFilePath ?: File("").absoluteFile.resolve(
                "${apk.nameWithoutExtension}-patched.apk",
            )

        val temporaryFilesPath =
            temporaryFilesPath ?: outputFilePath.parentFile.resolve(
                "${outputFilePath.nameWithoutExtension}-temporary-files",
            )

        val keystoreFilePath =
            keyStoreFilePath ?: outputFilePath.parentFile
                .resolve("${outputFilePath.nameWithoutExtension}.keystore")

        val installer = if (deviceSerial != null) {
            val deviceSerial = deviceSerial!!.ifEmpty { null }

            try {
                if (mount) {
                    AdbRootInstaller(deviceSerial)
                } else {
                    AdbInstaller(deviceSerial)
                }
            } catch (_: DeviceNotFoundException) {
                if (deviceSerial?.isNotEmpty() == true) {
                    logger.severe(
                        "Device with serial $deviceSerial not found to install to. " +
                            "Ensure the device is connected and the serial is correct when using the --install option.",
                    )
                } else {
                    logger.severe(
                        "No device has been found to install to. " +
                            "Ensure a device is connected when using the --install option.",
                    )
                }

                return EXIT_CODE_ERROR
            }
        } else {
            null
        }

        // endregion

        val patchingResult = PatchingResult()
        var mergedApkToCleanup: File? = null

        // Lightweight snapshot of patch metadata for use in finally block (auto-update).
        // Lightweight snapshot of current bundle metadata for use in finally block (auto-update).
        // The heavy Patch objects hold DEX classloaders and must not leak into finally.
        var patchesSnapshot: PatchBundle? = null

        try {
            logger.info("Loading patches")
            val patches: MutableSet<Patch<*>> = loadPatchesFromJar(patchesFiles).toMutableSet()
            patchesSnapshot = patches.toPatchBundle(sourceFiles = patchesFiles)

            // region Parse options JSON

            val patchOptionsBundle: PatchBundle? = optionsFilePath?.let { file ->
                if (file.exists()) {
                    logger.info("Reading options from ${file.path}")
                    val bundles = Json.decodeFromString<List<PatchBundle>>(file.readText())
                    bundles.findMatchingBundle(patchesFiles)
                } else {
                    logger.info("Options file ${file.path} does not exist, generating with defaults")
                    val bundle = patches.toPatchBundle(sourceFiles = patchesFiles)
                    val json = Json { prettyPrint = true }
                    file.absoluteFile.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(listOf(bundle)))
                    logger.info("Generated options file at ${file.path}")
                    bundle
                }
            }

            // Build enable/disable sets from JSON (lowercase for case-insensitive matching)
            val jsonEnabledPatches = patchOptionsBundle?.patches
                ?.filter { (_, entry) -> entry.enabled }
                ?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()
            val jsonDisabledPatches = patchOptionsBundle?.patches
                ?.filter { (_, entry) -> !entry.enabled }
                ?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()

            // Build options map from JSON, deserializing values using each patch's option types
            val jsonOptionsMap: Map<String, Map<String, Any?>> = patchOptionsBundle?.patches
                ?.mapNotNull { (patchName, entry) ->
                    if (entry.options.isEmpty()) return@mapNotNull null
                    val patch = patches.firstOrNull { it.name.equals(patchName, ignoreCase = true) }
                        ?: return@mapNotNull null
                    val resolvedName = patch.name ?: return@mapNotNull null
                    val deserializedOptions = entry.options.mapNotNull { (key, element) ->
                        if (!patch.options.containsKey(key)) return@mapNotNull null
                        val option = patch.options[key]
                        try {
                            key to deserializeOptionValue(element, option.type)
                        } catch (e: Exception) {
                            logger.warning("Failed to deserialize option \"$key\" for \"$patchName\": ${e.message}")
                            null
                        }
                    }.toMap()
                    if (deserializedOptions.isEmpty()) null else resolvedName to deserializedOptions
                }?.toMap() ?: emptyMap()

            // endregion

            val patcherTemporaryFilesPath = temporaryFilesPath.resolve("patcher")

            // Checking if the file is in apkm format (like reddit)
            val inputApk = if (apk.extension.equals("apkm", ignoreCase = true)) {
                logger.info("Merging APKM bundle")

                // Save merged APK to output directory (will be cleaned up after patching)
                val outputApk = outputFilePath.parentFile.resolve("${apk.nameWithoutExtension}-merged.apk")

                // Use APKEditor's Merger directly (handles extraction and merging)
                val mergerOptions = MergerOptions().apply {
                    inputFile = apk  // Original APKM file
                    outputFile = outputApk
                    cleanMeta = true
                }
                Merger(mergerOptions).run()

                mergedApkToCleanup = outputApk
                outputApk
            } else {
                apk
            }

            val (packageName, patcherResult) = Patcher(
                PatcherConfig(
                    inputApk,
                    patcherTemporaryFilesPath,
                    aaptBinaryPath?.path,
                    patcherTemporaryFilesPath.absolutePath,
                    true,
                ),
            ).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.packageVersion

                patchingResult.packageName = packageName
                patchingResult.packageVersion = packageVersion

                // Warn if options file is out of date (only for patches compatible with this app)
                if (patchOptionsBundle != null && optionsFilePath?.exists() == true && !updateOptions) {
                    val compatiblePatchNames = patches
                        .filter { patch ->
                            patch.compatiblePackages == null ||
                                patch.compatiblePackages!!.any { (name, _) -> name == packageName }
                        }
                        .mapNotNull { it.name?.lowercase() }
                        .toSet()
                    // All patch names in the .mpp regardless of app compatibility.
                    // Used for "removed" detection: a patch is only truly removed if it's
                    // gone from the .mpp entirely, not just incompatible with this app.
                    val allMppPatchNames = patches.mapNotNull { it.name?.lowercase() }.toSet()
                    val jsonPatchNames = patchOptionsBundle.patches.keys.map { it.lowercase() }.toSet()

                    val newPatches = compatiblePatchNames - jsonPatchNames
                    val removedPatches = jsonPatchNames - allMppPatchNames

                    // Check for new option keys within existing patches
                    var patchesWithNewOptions = 0
                    for ((patchName, snapshotEntry) in patchesSnapshot.patches) {
                        if (patchName.lowercase() !in compatiblePatchNames) continue
                        val jsonEntry = patchOptionsBundle.patches.entries
                            .firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value
                            ?: continue
                        val newOptionKeys = snapshotEntry.options.keys - jsonEntry.options.keys
                        if (newOptionKeys.isNotEmpty()) patchesWithNewOptions++
                    }

                    if (newPatches.isNotEmpty() || removedPatches.isNotEmpty() || patchesWithNewOptions > 0) {
                        logger.warning("Your options file is out of date with the current patches:")
                        if (newPatches.isNotEmpty()) {
                            logger.warning("  ${newPatches.size} new patches not in your options file, default patch values will be applied")
                        }
                        if (removedPatches.isNotEmpty()) {
                            logger.warning("  ${removedPatches.size} patches in your options file no longer exist and will be ignored")
                        }
                        if (patchesWithNewOptions > 0) {
                            logger.warning("  $patchesWithNewOptions patches have new options not in your file, default patch values will be applied")
                        }
                        logger.warning("  Use --options-update parameter to sync, or use 'options-create' command to regenerate.")
                    }
                }

                logger.info("Setting patch options")

                // Scope filteredPatches inside let so it goes out of scope immediately after
                // patcher += filteredPatches, matching the pattern from PR #54.
                patches.filterPatchSelection(
                    packageName,
                    packageVersion,
                    jsonEnabledPatches,
                    jsonDisabledPatches,
                ).let { filteredPatches ->
                    val patchesList = patches.toList()
                    val cliOptionsMap = selection.filter { it.enabled != null }.associate {
                        val enabledSelection = it.enabled!!

                        val resolvedName = enabledSelection.selector.name?.let { userInput ->
                            patchesList.firstOrNull { it.name.equals(userInput, ignoreCase = true) }?.name ?: userInput
                        } ?: patchesList[enabledSelection.selector.index!!].name!!

                        resolvedName to enabledSelection.options
                    }

                    (jsonOptionsMap.keys + cliOptionsMap.keys).associateWith { patchName ->
                        val jsonOpts = jsonOptionsMap[patchName] ?: emptyMap()
                        val cliOpts = cliOptionsMap[patchName] ?: emptyMap()

                        // Log when CLI options override JSON values
                        for ((key, cliValue) in cliOpts) {
                            val jsonValue = jsonOpts[key]
                            if (jsonValue != null && jsonValue != cliValue) {
                                logger.info("CLI option overrides JSON for \"$patchName\" -> \"$key\": $jsonValue -> $cliValue")
                            }
                        }

                        jsonOpts + cliOpts // CLI entries override JSON entries for same key
                    }.let(filteredPatches::setOptions)

                    patcher += filteredPatches
                }   // filteredPatches and patchesList go out of scope here

                // Execute patches.
                patchingResult.addStepResult(
                    PatchingStep.PATCHING,
                    {
                        runBlocking {
                            patcher().collect { patchResult ->
                                patchResult.exception?.let { exception ->
                                    StringWriter().use { writer ->
                                        exception.printStackTrace(PrintWriter(writer))

                                        logger.severe("\"${patchResult.patch}\" failed:\n$writer")

                                        patchingResult.failedPatches.add(
                                            FailedPatch(
                                                patchResult.patch.toSerializablePatch(),
                                                writer.toString()
                                            )
                                        )
                                        patchingResult.success = false

                                        if (!continueOnError) {
                                            throw PatchFailedException(
                                                "\"${patchResult.patch}\" failed",
                                                exception
                                            )
                                        }
                                    }
                                } ?: patchResult.patch.let {
                                    patchingResult.appliedPatches.add(patchResult.patch.toSerializablePatch())
                                    logger.info("\"${patchResult.patch}\" succeeded")
                                }
                            }
                        }
                    }
                )

                // patches lives in the outer try scope (needed for patchesSnapshot and options
                // file generation before the Patcher block). Clear it explicitly now — after
                // patcher() finishes and before patcher.get() — so the JVM can GC the DEX
                // classloaders before the most memory-intensive step.
                patches.clear()

                patcher.context.packageMetadata.packageName to patcher.get()
            }

            // region Save.

            inputApk.copyTo(temporaryFilesPath.resolve(inputApk.name), overwrite = true).apply {
                patchingResult.addStepResult(
                    PatchingStep.REBUILDING,
                    {
                        patcherResult.applyTo(this)
                    }
                )
            }.also { rebuiltApk ->
                if (striplibs.isNotEmpty()) {
                    patchingResult.addStepResult(
                        PatchingStep.STRIPPING_LIBS,
                        {
                            ApkLibraryStripper.stripLibraries(rebuiltApk, striplibs) { msg ->
                                logger.info(msg)
                            }
                        }
                    )
                }
            }.let { patchedApkFile ->
                if (!mount && !unsigned) {
                    patchingResult.addStepResult(
                        PatchingStep.SIGNING,
                        {
                            ApkUtils.signApk(
                                patchedApkFile,
                                outputFilePath,
                                signer,
                                ApkUtils.KeyStoreDetails(
                                    keystoreFilePath,
                                    keyStorePassword,
                                    keyStoreEntryAlias,
                                    keyStoreEntryPassword,
                                ),
                            )
                        }
                    )
                } else {
                    patchedApkFile.copyTo(outputFilePath, overwrite = true)
                }
            }

            logger.info("Saved to $outputFilePath")

            // endregion

            // region Install.

            deviceSerial?.let {
                patchingResult.addStepResult(
                    PatchingStep.INSTALLING,
                    {
                        runBlocking {
                            val result = installer!!.install(Installer.Apk(outputFilePath, packageName))
                            when (result) {
                                RootInstallerResult.FAILURE -> {
                                    logger.severe("Failed to mount the patched APK file")
                                    throw IllegalStateException("Failed to mount the patched APK file")
                                }
                                is AdbInstallerResult.Failure -> {
                                    logger.severe(result.exception.toString())
                                    throw result.exception
                                }
                                else -> logger.info("Installed the patched APK file")
                            }
                        }
                    }
                )
            }

            // endregion
        } catch (e: PatchFailedException) {
            logger.severe("Patching aborted: ${e.message}")
            logger.info(
                "Use --continue-on-error to skip failed patches and continue patching"
            )
            return EXIT_CODE_ERROR
        } catch (e: Exception) {
            // Should never happen.
            logger.severe("An unexpected error occurred: ${e.message}")
            e.printStackTrace()
            return EXIT_CODE_ERROR
        } finally {
            patchingResultOutputFilePath?.let { outputFile ->
                outputFile.outputStream().use { outputStream ->
                    Json.encodeToStream(patchingResult, outputStream)
                }
                logger.info("Patching result saved to $outputFile")
            }

            // Auto-update options JSON file using lightweight snapshot (no DEX references)
            val snapshot = patchesSnapshot
            if (optionsFilePath != null && updateOptions && snapshot != null) {
                try {
                    val existingBundles = optionsFilePath!!.let { file ->
                        if (file.exists()) {
                            try { Json.decodeFromString<List<PatchBundle>>(file.readText()) }
                            catch (e: Exception) { emptyList() }
                        } else emptyList()
                    }
                    val existingBundle = existingBundles.findMatchingBundle(patchesFiles)
                    val updatedBundle = snapshot.mergeWith(existingBundle)
                    val updatedBundles = existingBundles.withUpdatedBundle(updatedBundle)
                    val json = Json { prettyPrint = true }
                    optionsFilePath!!.writeText(json.encodeToString(updatedBundles))
                    logger.info("Updated options file ${optionsFilePath!!.path}")
                } catch (e: Exception) {
                    logger.warning("Failed to update options file: ${e.message}")
                }
            }

            if (purge) {
                logger.info("Purging temporary files")
                purge(temporaryFilesPath)
            }

            // Clean up merged APK if we created one from APKM
            mergedApkToCleanup?.let {
                if (!it.delete()) {
                    logger.warning("Could not clean up merged APK: ${it.path}")
                }
            }
        }

        return EXIT_CODE_SUCCESS
    }

    /**
     * Filter the patches based on the selection.
     *
     * @param packageName The package name of the APK file to be patched.
     * @param packageVersion The version of the APK file to be patched.
     * @param jsonEnabledPatches Patch names enabled via JSON options file (lowercase).
     * @param jsonDisabledPatches Patch names disabled via JSON options file (lowercase).
     * @return The filtered patches.
     */
    private fun Set<Patch<*>>.filterPatchSelection(
        packageName: String,
        packageVersion: String,
        jsonEnabledPatches: Set<String> = emptySet(),
        jsonDisabledPatches: Set<String> = emptySet(),
    ): Set<Patch<*>> = buildSet {
        // CLI flags (take precedence over JSON)
        val cliEnabledByName =
            selection.mapNotNull { it.enabled?.selector?.name?.lowercase() }.toSet()
        val cliEnabledByIndex =
            selection.mapNotNull { it.enabled?.selector?.index }.toSet()
        val cliDisabledByName =
            selection.mapNotNull { it.disable?.selector?.name?.lowercase() }.toSet()
        val cliDisabledByIndex =
            selection.mapNotNull { it.disable?.selector?.index }.toSet()

        this@filterPatchSelection.withIndex().forEach patchLoop@{ (i, patch) ->
            val patchName = patch.name!!
            val patchNameLower = patchName.lowercase()

            // Check package compatibility first to avoid duplicate logs for multi-app patches.
            patch.compatiblePackages?.let { packages ->
                packages.singleOrNull { (name, _) -> name == packageName }?.let { (_, versions) ->
                    if (versions?.isEmpty() == true) {
                        return@patchLoop logger.warning("\"$patchName\" incompatible with \"$packageName\"")
                    }

                    val matchesVersion =
                        force || versions?.let { it.any { version -> version == packageVersion } } ?: true

                    if (!matchesVersion) {
                        return@patchLoop logger.warning(
                            "\"$patchName\" incompatible with $packageName $packageVersion " +
                                "but compatible with " +
                                packages.joinToString("; ") { (packageName, versions) ->
                                    packageName + " " + versions!!.joinToString(", ")
                                },
                        )
                    }
                } ?: return@patchLoop logger.fine(
                    "\"$patchName\" incompatible with $packageName. " +
                        "It is only compatible with " +
                        packages.joinToString(", ") { (name, _) -> name },
                )

                return@let
            } ?: logger.fine("\"$patchName\" has no package constraints")

            // CLI flags take precedence over JSON, JSON takes precedence over defaults
            val isCliDisabled = patchNameLower in cliDisabledByName || i in cliDisabledByIndex
            if (isCliDisabled) {
                if (patchNameLower in jsonEnabledPatches) {
                    logger.info("\"$patchName\" disabled manually (overrides options file: enabled)")
                } else {
                    logger.info("\"$patchName\" disabled manually")
                }
                return@patchLoop
            }

            val isCliEnabled = patchNameLower in cliEnabledByName || i in cliEnabledByIndex
            if (isCliEnabled && patchNameLower in jsonDisabledPatches) {
                logger.info("\"$patchName\" enabled manually (overrides options file: disabled)")
            }

            // JSON-sourced enable/disable (only applies if no CLI flag for this patch)
            val isJsonDisabled = !isCliEnabled && patchNameLower in jsonDisabledPatches
            if (isJsonDisabled) return@patchLoop logger.info("\"$patchName\" disabled via options file")

            val isJsonEnabled = patchNameLower in jsonEnabledPatches

            val isEnabled = !exclusive && patch.use

            if (!(isEnabled || isCliEnabled || isJsonEnabled)) {
                return@patchLoop logger.info("\"$patchName\" disabled")
            }

            add(patch)

            logger.fine("\"$patchName\" added")
        }
    }

    private fun purge(resourceCachePath: File) {
        val result =
            if (resourceCachePath.deleteRecursively()) {
                "Purged resource cache directory"
            } else {
                "Failed to purge resource cache directory"
            }
        logger.info(result)
    }
}

private class PatchFailedException(message: String, cause: Throwable) : Exception(message, cause)
