package app.morphe.cli.command

import app.morphe.cli.command.model.PatchBundle
import app.morphe.cli.command.model.findMatchingBundle
import app.morphe.cli.command.model.mergeWithBundle
import app.morphe.cli.command.model.withUpdatedBundle
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.serialization.json.Json
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import java.util.concurrent.Callable
import java.util.logging.Logger

@Command(
    name = "options-create",
    description = ["Create an options JSON file for the patches and options."],
)
internal object OptionsCommand : Callable<Int> {

    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    private val logger = Logger.getLogger(this::class.java.name)

    @Spec
    private lateinit var spec: CommandSpec

    @CommandLine.Option(
        names = ["-p", "--patches"],
        description = ["One or more paths to MPP files."],
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
        names = ["-o", "--out"],
        description = ["Path to the output JSON file."],
        required = true,
    )
    private lateinit var outputFile: File

    @CommandLine.Option(
        names = ["-f", "--filter-package-name"],
        description = ["Filter patches by compatible package name."],
    )
    private var packageName: String? = null

    private val json = Json { prettyPrint = true }

    override fun call(): Int {
        return try {
            logger.info("Loading patches")

            val patches = loadPatchesFromJar(patchesFiles)

            val filtered = packageName?.let { pkg ->
                patches.filter { patch ->
                    patch.compatiblePackages?.any { (name, _) -> name == pkg } ?: true
                }.toSet()
            } ?: patches

            // Read existing bundles list if the file already exists
            val existingBundles: List<PatchBundle>? = if (outputFile.exists()) {
                try {
                    Json.decodeFromString<List<PatchBundle>>(outputFile.readText())
                } catch (e: Exception) {
                    logger.warning("Could not parse existing file, creating fresh: ${e.message}")
                    null
                }
            } else null

            // Find the bundle matching the current .mpp file(s), merge with it (or create fresh)
            val existingBundle = existingBundles?.findMatchingBundle(patchesFiles)
            val updatedBundle = filtered.mergeWithBundle(
                existing = existingBundle,
                sourceFiles = patchesFiles,
            )

            // Replace the matching entry in the list (or start a new list)
            val updatedBundles = existingBundles?.withUpdatedBundle(updatedBundle)
                ?: listOf(updatedBundle)

            outputFile.absoluteFile.parentFile?.mkdirs()
            outputFile.writeText(json.encodeToString(updatedBundles))

            if (existingBundle != null) {
                val existingNames = existingBundle.patches.keys.map { it.lowercase() }.toSet()
                val newNames = updatedBundle.patches.keys.map { it.lowercase() }.toSet()
                val added = newNames - existingNames
                val removed = existingNames - newNames
                val kept = newNames.intersect(existingNames)
                logger.info("Updated bundle in options file at ${outputFile.path}")
                logger.info("  ${kept.size} patches preserved, ${added.size} added, ${removed.size} removed")
            } else {
                logger.info("Created new bundle in options file at ${outputFile.path} with ${updatedBundle.patches.size} patches")
            }

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            logger.severe("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
