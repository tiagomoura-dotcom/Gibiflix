package app.gibiflix.ocr

import android.util.Log
import app.gibiflix.core.pkg.Panel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Tradução no aparelho (ML Kit). Feita na preparação e gravada no pacote (`translations`), para os
 * aparelhos sem Google Play Services só exibirem.
 */
class BalloonTranslator private constructor(private val target: String, private val translator: Translator) : Closeable {

    fun translate(panels: List<Panel>): List<Panel> = panels.map { p ->
        val blocks = p.text ?: return@map p
        val translated = blocks.map { b ->
            val out = runCatching { Tasks.await(translator.translate(b.value), 30, TimeUnit.SECONDS) }.getOrNull() ?: b.value
            b.copy(value = out)
        }
        p.copy(translations = (p.translations ?: emptyMap()) + (target to translated))
    }

    override fun close() = translator.close()

    companion object {
        /** null se o idioma não é suportado ou o modelo não pôde ser baixado. */
        fun create(target: String, source: String? = null): BalloonTranslator? = try {
            val tgt = TranslateLanguage.fromLanguageTag(target) ?: return null
            val src = TranslateLanguage.fromLanguageTag(source ?: "en") ?: TranslateLanguage.ENGLISH
            if (src == tgt) return null
            val t = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
            Tasks.await(t.downloadModelIfNeeded(DownloadConditions.Builder().build()), 5, TimeUnit.MINUTES)
            BalloonTranslator(target, t)
        } catch (t: Throwable) {
            Log.w("Gibiflix", "Tradução indisponível: ${t.message}")
            null
        }
    }
}
