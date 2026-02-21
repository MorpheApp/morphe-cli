package app.morphe.cli.command.model

enum class PatchingStep {
    PATCHING,
    REBUILDING,
    STRIPPING_LIBS,
    SIGNING,
    INSTALLING
}