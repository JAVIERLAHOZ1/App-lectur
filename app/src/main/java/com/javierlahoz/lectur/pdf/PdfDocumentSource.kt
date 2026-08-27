package com.javierlahoz.lectur.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Envoltorio sobre [PdfRenderer] (el motor de PDF que ya trae Android, sin
 * librerias externas). PdfRenderer no admite accesos simultaneos, asi que todo
 * pasa por un mutex y se ejecuta fuera del hilo principal.
 */
class PdfDocumentSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    private val lock = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Relacion alto/ancho de la pagina, para reservar el hueco antes de pintarla. */
    private val ratios = arrayOfNulls<Float>(pageCount)

    suspend fun aspectRatio(index: Int): Float = withContext(Dispatchers.IO) {
        ratios.getOrNull(index)?.let { return@withContext it }
        lock.withLock {
            if (closed) return@withLock DEFAULT_RATIO
            val page = renderer.openPage(index)
            try {
                val ratio = if (page.width > 0) page.height.toFloat() / page.width else DEFAULT_RATIO
                ratios[index] = ratio
                ratio
            } finally {
                page.close()
            }
        }
    }

    /** Dibuja una pagina con el ancho indicado en pixeles. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            val page = renderer.openPage(index)
            try {
                val width = widthPx.coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX)
                val ratio = if (page.width > 0) page.height.toFloat() / page.width else DEFAULT_RATIO
                ratios[index] = ratio
                val height = (width * ratio).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // PdfRenderer no pinta el fondo: hay que dejarlo blanco a mano.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (t: Throwable) {
                null
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val DEFAULT_RATIO = 1.414f
        private const val MIN_WIDTH_PX = 120
        private const val MAX_WIDTH_PX = 2600

        fun open(file: File): PdfDocumentSource {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfDocumentSource(descriptor, PdfRenderer(descriptor))
            } catch (t: Throwable) {
                runCatching { descriptor.close() }
                throw t
            }
        }
    }
}
