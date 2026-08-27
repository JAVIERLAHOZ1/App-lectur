package com.javierlahoz.lectur.pdf

import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Una entrada del indice del libro (capitulo, seccion...). */
data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    val level: Int
)

/** Una palabra suelta de una pagina, con su recuadro en puntos PDF. */
private data class PageWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float, margin: Float): Boolean =
        x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin

    fun distanceTo(x: Float, y: Float): Float {
        val dx = max(0f, max(left - x, x - right))
        val dy = max(0f, max(top - y, y - bottom))
        return dx + dy * 2f // penaliza mas la distancia vertical: no saltar de linea
    }
}

/**
 * Lee la parte "de texto" del PDF con PdfBox: el indice y las palabras con su
 * posicion, que es lo que hace falta para el diccionario. Se abre solo cuando
 * de verdad se usa, porque para un libro grande cuesta unos segundos.
 */
class PdfTextIndex(private val file: File) : Closeable {

    private val lock = Mutex()
    private var document: PDDocument? = null
    private var openFailed = false
    private var closed = false

    private var cachedOutline: List<OutlineEntry>? = null
    private val wordCache = LruCache<Int, List<PageWord>>(6)

    private suspend fun open(): PDDocument? = lock.withLock {
        if (closed || openFailed) return@withLock null
        document?.let { return@withLock it }
        val loaded = runCatching { PDDocument.load(file) }.getOrNull()
        if (loaded == null) {
            openFailed = true
        } else {
            document = loaded
        }
        loaded
    }

    /** Indice del libro; lista vacia si el PDF no trae ninguno. */
    suspend fun outline(): List<OutlineEntry> = withContext(Dispatchers.IO) {
        cachedOutline?.let { return@withContext it }
        val doc = open() ?: return@withContext emptyList()
        val entries = runCatching { readOutline(doc) }.getOrElse { emptyList() }
        cachedOutline = entries
        entries
    }

    /**
     * Palabra que hay en la posicion indicada de una pagina, en coordenadas
     * relativas (0f..1f desde la esquina superior izquierda de la pagina).
     */
    suspend fun wordAt(pageIndex: Int, fractionX: Float, fractionY: Float): String? =
        withContext(Dispatchers.IO) {
            val doc = open() ?: return@withContext null
            if (pageIndex !in 0 until doc.numberOfPages) return@withContext null

            val words = wordsOf(doc, pageIndex)
            if (words.isEmpty()) return@withContext null

            val page = doc.getPage(pageIndex)
            val box = page.cropBox
            val rotated = abs(page.rotation / 90) % 2 == 1
            val pageWidth = if (rotated) box.height else box.width
            val pageHeight = if (rotated) box.width else box.height

            val x = fractionX * pageWidth
            val y = fractionY * pageHeight

            val hit = words.firstOrNull { it.contains(x, y, TOUCH_MARGIN_PT) }
                ?: words.minByOrNull { it.distanceTo(x, y) }
                    ?.takeIf { it.distanceTo(x, y) <= NEAREST_LIMIT_PT }

            hit?.text
        }

    private fun wordsOf(document: PDDocument, pageIndex: Int): List<PageWord> {
        wordCache.get(pageIndex)?.let { return it }
        val words = runCatching { extractWords(document, pageIndex) }.getOrElse { emptyList() }
        wordCache.put(pageIndex, words)
        return words
    }

    private fun extractWords(document: PDDocument, pageIndex: Int): List<PageWord> {
        val words = ArrayList<PageWord>()

        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                var builder = StringBuilder()
                var left = 0f
                var top = 0f
                var right = 0f
                var bottom = 0f

                fun flush() {
                    if (builder.isNotEmpty()) {
                        words += PageWord(builder.toString(), left, top, right, bottom)
                        builder = StringBuilder()
                    }
                }

                for (position in textPositions) {
                    val glyph = position.unicode ?: continue
                    if (glyph.isBlank()) {
                        flush()
                        continue
                    }
                    val glyphLeft = position.xDirAdj
                    val glyphRight = glyphLeft + position.widthDirAdj
                    val glyphBottom = position.yDirAdj
                    val glyphTop = glyphBottom - position.heightDir

                    if (builder.isEmpty()) {
                        left = glyphLeft
                        right = glyphRight
                        top = glyphTop
                        bottom = glyphBottom
                    } else {
                        left = min(left, glyphLeft)
                        right = max(right, glyphRight)
                        top = min(top, glyphTop)
                        bottom = max(bottom, glyphBottom)
                    }
                    builder.append(glyph)
                }
                flush()
            }
        }

        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)

        return words
    }

    private fun readOutline(document: PDDocument): List<OutlineEntry> {
        val root = document.documentCatalog?.documentOutline ?: return emptyList()
        val entries = ArrayList<OutlineEntry>()
        collect(document, root.firstChild, 0, entries)
        return entries.filter { it.pageIndex >= 0 }
    }

    private fun collect(
        document: PDDocument,
        first: PDOutlineItem?,
        level: Int,
        into: MutableList<OutlineEntry>
    ) {
        var item = first
        var guard = 0
        while (item != null && guard < MAX_OUTLINE_ENTRIES && into.size < MAX_OUTLINE_ENTRIES) {
            guard++
            val page = runCatching { item?.findDestinationPage(document) }.getOrNull()
            val index = if (page != null) {
                runCatching { document.pages.indexOf(page) }.getOrDefault(-1)
            } else {
                -1
            }
            val title = item?.title?.trim().orEmpty()
            if (title.isNotEmpty()) {
                into += OutlineEntry(title = title, pageIndex = index, level = level)
            }
            if (level < MAX_OUTLINE_DEPTH) {
                collect(document, item?.firstChild, level + 1, into)
            }
            item = item?.nextSibling
        }
    }

    override fun close() {
        closed = true
        runCatching { document?.close() }
        document = null
        wordCache.evictAll()
    }

    private companion object {
        /** Margen de tolerancia al tocar, en puntos PDF (1 pt ~ 0,35 mm). */
        const val TOUCH_MARGIN_PT = 2f
        const val NEAREST_LIMIT_PT = 24f
        const val MAX_OUTLINE_ENTRIES = 800
        const val MAX_OUTLINE_DEPTH = 3
    }
}

/** Deja la palabra lista para buscarla: sin comillas, puntos ni guiones sueltos. */
fun cleanWord(raw: String): String =
    raw.trim().trim { !it.isLetter() && it != '-' && it != '\'' }
        .trimEnd('-')
        .lowercase()
