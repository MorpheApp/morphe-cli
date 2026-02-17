package app.morphe.cli.command

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

            val patchOptionsFile = filtered.toPatchOptionsFile(
                sourcePatches = patchesFiles.joinToString(", ") { it.name }
            )
            val jsonString = json.encodeToString(patchOptionsFile)

            outputFile.absoluteFile.parentFile?.mkdirs()
            outputFile.writeText(jsonString)

            logger.info("Exported ${patchOptionsFile.patches.size} patches to ${outputFile.path}")

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            logger.severe("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
