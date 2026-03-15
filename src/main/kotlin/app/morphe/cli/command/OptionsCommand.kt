package app.morphe.cli.command

import app.morphe.cli.command.model.PatchBundle
import app.morphe.cli.command.model.findMatchingBundle
import app.morphe.cli.command.model.mergeWithBundle
import app.morphe.cli.command.model.withUpdatedBundle
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.serialization.json.Json
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Help.Visibility.ALWAYS
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

    @Option(
        names = ["-p", "--patches"],
        description = ["Path to a MPP file or a GitHub repo url such as https://github.com/MorpheApp/morphe-patches"],
        required = true,
    )
    @Suppress("unused")
    private fun setPatchesFile(patchesFiles: Set<File>) {
        this.patchesFiles = checkFileExistsOrIsUrl(patchesFiles, spec)
    }

    private var patchesFiles = emptySet<File>()

    @Option(
        names = ["-o", "--out"],
        description = ["Path to the output JSON file."],
        required = true,
    )
    private lateinit var outputFile: File

    @Option(
        names = ["--prerelease"],
        description = ["Fetch the latest dev pre-release instead of the stable main release from the repo provided in --patches."],
        showDefaultValue = ALWAYS,
    )
    private var prerelease: Boolean = false

    @Option(
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    @Option(
        names = ["-f", "--filter-package-name"],
        description = ["Filter patches by compatible package name."],
    )
    private var packageName: String? = null

    private val json = Json { prettyPrint = true }

    override fun call(): Int {
        val temporaryFilesPath = temporaryFilesPath ?: File("").absoluteFile.resolve("morphe-temporary-files")

        try {
            patchesFiles = PatchFileResolver.resolve(
                patchesFiles,
                prerelease,
                temporaryFilesPath
            )
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                e.message ?: "Failed to resolve patch URL"
            )
        }

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
