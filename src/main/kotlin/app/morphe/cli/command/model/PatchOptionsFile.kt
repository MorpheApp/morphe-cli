package app.morphe.cli.command.model

import app.morphe.patcher.patch.Patch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class PatchOptionsFile(
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("source_patches")
    val sourcePatches: String? = null,
    val patches: Map<String, PatchEntry>,
)

@Serializable
data class PatchEntry(
    val enabled: Boolean,
    val options: Map<String, JsonElement> = emptyMap(),
)

private fun now(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

/**
 * Converts a set of loaded patches to a [PatchOptionsFile] for JSON export.
 *
 * @param sourcePatches optional name(s) of the source .mpp file(s) used to generate this file.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Set<Patch<*>>.toPatchOptionsFile(sourcePatches: String? = null): PatchOptionsFile {
    val entries = this
        .filter { it.name != null }
        .associate { patch ->
            patch.name!! to PatchEntry(
                enabled = patch.use,
                options = patch.options.mapValues { (_, option) ->
                    PatchSerializer.serializeValue(option.default)
                },
            )
        }
    return PatchOptionsFile(
        createdAt = now(),
        sourcePatches = sourcePatches,
        patches = entries,
    )
}

/**
 * Deserializes a [JsonElement] to a typed value based on the option's [KType].
 */
fun deserializeOptionValue(element: JsonElement, type: KType): Any? {
    if (element is JsonNull) return null

    if (element is JsonPrimitive) {
        val classifier = type.classifier
        return when (classifier) {
            Boolean::class -> element.booleanOrNull
                ?: throw IllegalArgumentException("Expected Boolean, got: $element")
            Int::class -> element.intOrNull
                ?: throw IllegalArgumentException("Expected Int, got: $element")
            Long::class -> element.longOrNull
                ?: throw IllegalArgumentException("Expected Long, got: $element")
            Float::class -> element.floatOrNull
                ?: throw IllegalArgumentException("Expected Float, got: $element")
            Double::class -> element.doubleOrNull
                ?: throw IllegalArgumentException("Expected Double, got: $element")
            String::class -> element.content
            else -> element.content
        }
    }

    if (element is JsonArray) {
        val elementType = type.arguments.firstOrNull()?.type ?: typeOf<String>()
        return element.map { deserializeOptionValue(it, elementType) }
    }

    return element.toString()
}
