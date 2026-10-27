package app.gibiflix.work

import android.content.Context
import app.gibiflix.R
import app.gibiflix.importer.ImportOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado das importações em andamento e avisos para a interface. */
class ImportCenter(private val context: Context) {
    data class Progress(val index: Int, val total: Int, val name: String, val fraction: Float)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun update(p: Progress?) { _progress.value = p }

    fun message(text: String) { _messages.tryEmit(text) }

    fun report(results: List<ImportOutcome>) {
        val imported = results.count { it is ImportOutcome.Imported }
        val dups = results.filterIsInstance<ImportOutcome.Duplicate>()
        val failed = results.filterIsInstance<ImportOutcome.Failed>()
        val parts = mutableListOf<String>()
        if (imported > 0) parts += context.resources.getQuantityString(R.plurals.import_done, imported, imported)
        dups.forEach { parts += context.getString(R.string.import_duplicate, it.name, it.existing.title) }
        failed.forEach { parts += context.getString(R.string.import_failed, it.name, it.reason) }
        if (parts.isEmpty()) return
        val text = parts.joinToString("\n")
        message(text)
        if (dups.isNotEmpty() || failed.isNotEmpty()) {
            Notifications.notify(
                context, Notifications.ID_IMPORT_RESULT,
                Notifications.simple(context, Notifications.CHANNEL_WORK, context.getString(R.string.import_notification_title), text),
            )
        }
    }
}
