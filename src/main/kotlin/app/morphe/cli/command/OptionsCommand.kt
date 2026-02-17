package app.morphe.cli.command

import app.morphe.cli.command.model.PatchOptionsFile
import app.morphe.cli.command.model.mergeWithOptionsFile
import app.morphe.cli.command.model.toPatchOptionsFile
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

            val sourcePatchesName = patchesFiles.joinToString(", ") { it.name }

            // Merge with existing file if it exists, otherwise create fresh
            val existing = if (outputFile.exists()) {
                try {
                    Json.decodeFromString<PatchOptionsFile>(outputFile.readText())
                } catch (e: Exception) {
                    logger.warning("Could not parse existing file, creating fresh: ${e.message}")
                    null
                }
            } else null

            val patchOptionsFile = filtered.mergeWithOptionsFile(
                existing = existing,
                sourcePatches = sourcePatchesName,
            )
            val jsonString = json.encodeToString(patchOptionsFile)

            outputFile.absoluteFile.parentFile?.mkdirs()
            outputFile.writeText(jsonString)

            if (existing != null) {
                val existingNames = existing.patches.keys.map { it.lowercase() }.toSet()
                val newNames = patchOptionsFile.patches.keys.map { it.lowercase() }.toSet()
                val added = newNames - existingNames
                val removed = existingNames - newNames
                val kept = newNames.intersect(existingNames)
                logger.info("Updated existing options file at ${outputFile.path}")
                logger.info("  ${kept.size} patch(es) preserved, ${added.size} added, ${removed.size} removed")
            } else {
                logger.info("Created new options file at ${outputFile.path} with ${patchOptionsFile.patches.size} patches")
            }

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            logger.severe("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
