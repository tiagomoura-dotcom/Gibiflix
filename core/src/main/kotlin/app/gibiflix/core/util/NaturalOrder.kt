package app.gibiflix.core.util

/** Ordem natural de nomes: "2" antes de "10", sem diferenciar maiúsculas. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]; val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                val lenDiff = (ei - i) - (ej - j)
                if (lenDiff != 0) return lenDiff
                i = ei; j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++; j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

object ArchiveNames {
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif", "bmp")
    private val SYSTEM = setOf(".ds_store", "thumbs.db", "desktop.ini")

    fun extension(name: String): String = name.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    /** Página válida: imagem, fora de pastas de sistema e sem ser arquivo oculto. */
    fun isPage(path: String): Boolean {
        val norm = path.replace('\\', '/')
        val parts = norm.split('/')
        if (parts.any { it.equals("__MACOSX", ignoreCase = true) }) return false
        val base = parts.last()
        if (base.isEmpty() || base.startsWith(".") || base.lowercase() in SYSTEM) return false
        return extension(base) in IMAGE_EXTENSIONS
    }

    fun isComicInfo(path: String): Boolean =
        path.replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

    fun sortPages(paths: List<String>): List<String> = paths.sortedWith { a, b ->
        NaturalOrder.compare(a.replace('\\', '/'), b.replace('\\', '/'))
    }

    /** Nome normalizado no pacote: 0001.jpg, 0002.png... */
    fun packageName(index: Int, ext: String): String {
        val e = when (ext.lowercase()) { "jpeg" -> "jpg"; else -> ext.lowercase() }
        return "%04d.%s".format(index + 1, e)
    }
}
