package org.piramalswasthya.stoptb.helpers

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.math.BigDecimal
import okio.Buffer

internal const val SYNC_PAYLOAD_LIMIT = 32 * 1024L

internal object SyncPayloadRedaction {
    private val sensitive = listOf("password", "token", "authorization", "secret", "credential", "cookie",
        "mobile", "phone", "email", "address", "abha", "aadhaar", "aadhar", "image", "photo", "signature",
        "dateofbirth", "latitude", "longitude", "contactnumber", "contactno")
    private val names = setOf("name", "benname", "benfirstname", "benlastname", "bensurname", "beneficiaryname",
        "firstname", "lastname", "middlename", "patientname", "fullname", "surname", "fathername", "mothername",
        "spousename", "familyheadname", "guardianname", "dob", "houseno", "housenumber")
    fun sensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().filter { it.isLetterOrDigit() }
        return normalized in names || sensitive.any { normalized.contains(it) }
    }

    fun body(text: String): String = redact(text, 0)

    fun backendStatus(text: String): Int? = runCatching {
        val reader = JsonReader.of(Buffer().writeUtf8(text))
        reader.beginObject()
        var code: Int? = null
        while (reader.hasNext()) {
            if (reader.nextName() == "statusCode") code = reader.nextString().toIntOrNull()
            else reader.skipValue()
        }
        code
    }.getOrNull()

    private fun redact(text: String, depth: Int): String = try {
        val reader = JsonReader.of(Buffer().writeUtf8(text))
        if (reader.peek() !in setOf(JsonReader.Token.BEGIN_OBJECT, JsonReader.Token.BEGIN_ARRAY)) {
            "[Non-object/array body omitted]"
        } else {
            val buffer = Buffer()
            val writer = JsonWriter.of(buffer).apply { indent = "  "; serializeNulls = true }
            copy(reader, writer, depth)
            check(reader.peek() == JsonReader.Token.END_DOCUMENT)
            writer.flush()
            buffer.readUtf8().let {
                if (it.length > 65536) it.take(65536) + "\n[Sanitized preview truncated at 64 KiB characters]" else it
            }
        }
    } catch (_: Exception) { "[Non-JSON or incomplete body omitted]" }

    private fun copy(reader: JsonReader, writer: JsonWriter, depth: Int) {
        if (depth > 20) { reader.skipValue(); writer.value("[Nested value omitted]"); return }
        when (reader.peek()) {
            JsonReader.Token.BEGIN_OBJECT -> {
                reader.beginObject(); writer.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    writer.name(name)
                    if (sensitiveKey(name)) { reader.skipValue(); writer.value("[REDACTED]") }
                    else copy(reader, writer, depth + 1)
                }
                reader.endObject(); writer.endObject()
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray(); writer.beginArray()
                while (reader.hasNext()) copy(reader, writer, depth + 1)
                reader.endArray(); writer.endArray()
            }
            // Keep large IDs exact; converting through Double can corrupt diagnostic IDs.
            JsonReader.Token.NUMBER -> writer.value(BigDecimal(reader.nextString()))
            JsonReader.Token.BOOLEAN -> writer.value(reader.nextBoolean())
            JsonReader.Token.NULL -> { reader.nextNull<Any>(); writer.nullValue() }
            JsonReader.Token.STRING -> {
                val value = reader.nextString()
                val trimmed = value.trim()
                val safe = when {
                    trimmed.startsWith("{") || trimmed.startsWith("[") -> redact(trimmed, depth + 1)
                    Regex("(?i)(password|token|secret|authorization)\\s*[:=]").containsMatchIn(value) -> "[Sensitive text redacted]"
                    else -> value.replace(Regex("(?i)(bearer|basic)\\s+[^\\s,;\\\"]+"), "[Authorization redacted]")
                }
                writer.value(safe)
            }
            else -> error("Unexpected JSON token")
        }
    }
}
