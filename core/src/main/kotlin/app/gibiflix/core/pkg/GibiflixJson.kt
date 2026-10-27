package app.gibiflix.core.pkg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Resultado da leitura de um gibiflix.json: o modelo e o JSON original (para preservar campos desconhecidos). */
data class ParsedDoc(val doc: GibiflixDoc, val raw: JsonObject)

sealed interface ReadResult {
    data class Ok(val parsed: ParsedDoc) : ReadResult
    /** Versão maior que a conhecida: tratar como CBZ comum e oferecer "Preparar de novo". */
    data class NewerVersion(val version: Int) : ReadResult
    data class Invalid(val reason: String) : ReadResult
}

object GibiflixJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun read(text: String): ReadResult {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return ReadResult.Invalid(e.message ?: "JSON inválido")
        }
        val obj = element as? JsonObject ?: return ReadResult.Invalid("não é um objeto")
        if ((obj["format"] as? JsonPrimitive)?.content != GibiflixDoc.FORMAT) return ReadResult.Invalid("format")
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: return ReadResult.Invalid("version")
        if (version > GibiflixDoc.VERSION) return ReadResult.NewerVersion(version)
        return try {
            ReadResult.Ok(ParsedDoc(json.decodeFromJsonElement(GibiflixDoc.serializer(), obj), obj))
        } catch (e: Exception) {
            ReadResult.Invalid(e.message ?: "campos inválidos")
        }
    }

    fun parse(text: String): ParsedDoc = when (val r = read(text)) {
        is ReadResult.Ok -> r.parsed
        is ReadResult.NewerVersion -> throw IllegalStateException("versão ${r.version} não suportada")
        is ReadResult.Invalid -> throw IllegalArgumentException(r.reason)
    }

    /** Escreve o documento mesclado sobre o JSON original, mantendo campos que este app não conhece. */
    fun write(doc: GibiflixDoc, original: JsonObject? = null): String {
        val fresh = json.encodeToJsonElement(doc) as JsonObject
        val merged = if (original == null) fresh else mergeDoc(original, fresh)
        return json.encodeToString(JsonElement.serializer(), merged)
    }

    private fun mergeDoc(old: JsonObject, new: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(old)
        for ((k, v) in new) {
            out[k] = when {
                k == "pages" && v is JsonArray && old[k] is JsonArray -> mergePages(old[k] as JsonArray, v)
                else -> mergeElement(old[k], v)
            }
        }
        // Campos conhecidos que ficaram nulos/ausentes no novo (ex.: trim removido) não voltam do antigo.
        for (k in KNOWN_DOC_KEYS) if (k !in new) out.remove(k)
        return JsonObject(out)
    }

    /** Páginas casadas pelo nome do arquivo; quadros casados por posição quando a quantidade não mudou. */
    private fun mergePages(old: JsonArray, new: JsonArray): JsonArray {
        val byFile = old.filterIsInstance<JsonObject>().associateBy { (it["file"] as? JsonPrimitive)?.content }
        return JsonArray(new.map { p ->
            val np = p as? JsonObject ?: return@map p
            val op = byFile[(np["file"] as? JsonPrimitive)?.content] ?: return@map np
            val out = LinkedHashMap<String, JsonElement>(op)
            for ((k, v) in np) {
                out[k] = if (k == "panels" && v is JsonArray && op[k] is JsonArray && (op[k] as JsonArray).size == v.size) {
                    JsonArray(v.mapIndexed { i, e -> mergeElement((op[k] as JsonArray)[i], e) })
                } else mergeElement(op[k], v)
            }
            for (k in KNOWN_PAGE_KEYS) if (k !in np) out.remove(k)
            JsonObject(out)
        })
    }

    private fun mergeElement(old: JsonElement?, new: JsonElement): JsonElement {
        if (old is JsonObject && new is JsonObject) {
            val out = LinkedHashMap<String, JsonElement>(old)
            for ((k, v) in new) out[k] = mergeElement(old[k], v)
            return JsonObject(out)
        }
        return new
    }

    private val KNOWN_DOC_KEYS = setOf("createdBy", "detector")
    private val KNOWN_PAGE_KEYS = setOf("trim", "confidence", "readingDirection")

    fun versionOf(obj: JsonObject): Int = obj["version"]?.jsonPrimitive?.int ?: 0
}
