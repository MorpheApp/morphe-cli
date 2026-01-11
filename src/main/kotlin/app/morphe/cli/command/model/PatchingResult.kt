package app.morphe.cli.command.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class PatchingResult(
    @EncodeDefault val appliedPatches: MutableList<SerializablePatch> = mutableListOf(),
    @EncodeDefault val failedPatches: MutableList<FailedPatch> = mutableListOf()
    // Maybe add results for compilation, APK aligning, signing?
)
