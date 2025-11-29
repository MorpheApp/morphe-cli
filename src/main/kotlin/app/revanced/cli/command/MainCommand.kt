package app.revanced.cli.command

import app.revanced.cli.command.utility.UtilityCommand
import app.revanced.library.logging.Logger
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import java.util.*

fun main(args: Array<String>) {
    Logger.setDefault()
    CommandLine(MainCommand).execute(*args).let(System::exit)
}

private object CLIVersionProvider : IVersionProvider {
    override fun getVersion() =
        arrayOf(
            MainCommand::class.java.getResourceAsStream(
                "/app/morphe/cli/version.properties",
            )?.use { stream ->
                Properties().apply {
                    load(stream)
                }.let {
                    "Morphe CLI v${it.getProperty("version")}"
                }
            } ?: "Morphe CLI",
        )
}

@Command(
    name = "morphe-cli",
    description = ["Command line application to use Morphe."],
    mixinStandardHelpOptions = true,
    versionProvider = CLIVersionProvider::class,
    subcommands = [
        PatchCommand::class,
        ListPatchesCommand::class,
        ListCompatibleVersions::class,
        UtilityCommand::class,
    ],
)
private object MainCommand
