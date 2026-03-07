package app.morphe

import app.morphe.library.logging.Logger

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        app.morphe.gui.launchGui(args)
    } else {
        Logger.setDefault()
        picocli.CommandLine(app.morphe.cli.command.MainCommand)
            .execute(*args)
            .let(System::exit)
    }
}
