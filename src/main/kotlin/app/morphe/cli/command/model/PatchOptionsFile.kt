package app.morphe.cli.command.model

import app.morphe.patcher.patch.Patch
import kotlinx.serialization.ExperimentalSerializationApi
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

@Serializable
data class PatchOptionsFile(
    val patches: Map<String, PatchEntry>,
)

@Serializable
data class PatchEntry(
    val enabled: Boolean,
    val options: Map<String, JsonElement> = emptyMap(),
)

/**
 * Converts a set of loaded patches to a [PatchOptionsFile] for JSON export.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Set<Patch<*>>.toPatchOptionsFile(): PatchOptionsFile {
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
    return PatchOptionsFile(patches = entries)
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
