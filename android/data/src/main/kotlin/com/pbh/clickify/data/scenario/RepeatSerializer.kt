package com.pbh.clickify.data.scenario

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Writes `"repeat"` as a bare integer or the bare string `until-stopped` (FS-9).
 *
 * The default encoding for a sealed interface would be an object with a discriminator, which is
 * accurate and unreadable. A person editing this file by hand should be able to type `50`.
 */
object RepeatSerializer : KSerializer<RepeatDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("repeat", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: RepeatDto,
    ) {
        val json = encoder as JsonEncoder
        when (value) {
            is RepeatDto.Count -> json.encodeJsonElement(JsonPrimitive(value.value))
            RepeatDto.UntilStopped -> json.encodeJsonElement(JsonPrimitive(RepeatDto.UntilStopped.TOKEN))
        }
    }

    override fun deserialize(decoder: Decoder): RepeatDto {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return RepeatDto.Count(1)
        primitive.intOrNull?.let { return RepeatDto.Count(it) }
        // FS-12: anything else is a value this build does not recognise, so it takes the default
        // rather than costing the user the file.
        return if (primitive.content == RepeatDto.UntilStopped.TOKEN) {
            RepeatDto.UntilStopped
        } else {
            RepeatDto.Count(1)
        }
    }
}
