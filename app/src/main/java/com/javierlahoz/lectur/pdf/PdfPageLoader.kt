package com.javierlahoz.lectur.pdf

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Paginas ya dibujadas, guardadas en memoria para que volver atras sea instantaneo. */
class PdfPageLoader(private val source: PdfDocumentSource) {

    val pageCount: Int = source.pageCount

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun aspectRatio(index: Int): Float = source.aspectRatio(index)

    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return it }
        val bitmap = source.render(index, widthPx) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun release() {
        cache.evictAll()
        source.close()
    }

    private companion object {
        fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return (maxKb / 4).coerceIn(16 * 1024, 192 * 1024)
        }
    }
}

sealed interface PdfLoadState {
    data object Loading : PdfLoadState
    data class Ready(val loader: PdfPageLoader) : PdfLoadState
    data class Failed(val message: String) : PdfLoadState
}

/**
 * Abre el PDF mientras la pantalla este visible y lo cierra al salir.
 */
@Composable
fun rememberPdfDocument(file: File): State<PdfLoadState> {
    val state = remember(file.absolutePath) { mutableStateOf<PdfLoadState>(PdfLoadState.Loading) }

    DisposableEffect(file.absolutePath) {
        val holder = LoaderHolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val result = runCatching { PdfDocumentSource.open(file) }
            result.onSuccess { source ->
                val loader = PdfPageLoader(source)
                if (holder.attach(loader)) {
                    state.value = PdfLoadState.Ready(loader)
                }
            }.onFailure {
                state.value = PdfLoadState.Failed(
                    it.message ?: "No se ha podido abrir el PDF"
                )
            }
        }
        onDispose {
            scope.cancel()
            holder.dispose()
            state.value = PdfLoadState.Loading
        }
    }

    return state
}

/** Evita fugas si la pantalla se cierra mientras el PDF todavia se esta abriendo. */
private class LoaderHolder {
    private var loader: PdfPageLoader? = null
    private var disposed = false

    @Synchronized
    fun attach(value: PdfPageLoader): Boolean {
        if (disposed) {
            value.release()
            return false
        }
        loader = value
        return true
    }

    @Synchronized
    fun dispose() {
        disposed = true
        loader?.release()
        loader = null
    }
}
