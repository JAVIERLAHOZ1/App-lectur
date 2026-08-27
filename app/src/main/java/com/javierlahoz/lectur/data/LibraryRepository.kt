package com.javierlahoz.lectur.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.javierlahoz.lectur.pdf.PdfDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Guarda la biblioteca (PDFs + portadas + progreso) en el almacenamiento
 * privado de la app. El indice es un simple fichero JSON, sin base de datos.
 */
class LibraryRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val booksDir = File(appContext.filesDir, "books")
    private val coversDir = File(appContext.filesDir, "covers")
    private val indexFile = File(appContext.filesDir, "library.json")
    private val ioLock = Mutex()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private var loaded = false

    suspend fun loadIfNeeded() {
        ioLock.withLock {
            if (loaded) return
            loaded = true
            _books.value = withContext(Dispatchers.IO) { readIndex() }
        }
    }

    fun fileFor(bookId: String): File = File(booksDir, "$bookId.pdf")

    fun coverFor(bookId: String): File = File(coversDir, "$bookId.png")

    fun book(bookId: String?): Book? = _books.value.firstOrNull { it.id == bookId }

    /** Copia el PDF a la app, calcula el numero de paginas y genera la portada. */
    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = fileFor(id)
        try {
            booksDir.mkdirs()
            coversDir.mkdirs()

            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(
                IllegalStateException("No se ha podido leer el archivo seleccionado")
            )

            val source = PdfDocumentSource.open(target)
            val pageCount = try {
                val count = source.pageCount
                if (count <= 0) throw IllegalStateException("El PDF no tiene paginas")
                source.render(0, COVER_WIDTH_PX)?.let { bitmap ->
                    writeCover(id, bitmap)
                    bitmap.recycle()
                }
                count
            } finally {
                source.close()
            }

            val book = Book(
                id = id,
                title = displayName(uri) ?: "Libro sin titulo",
                pageCount = pageCount,
                lastPage = 0,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = 0L
            )
            update { current -> current + book }
            Result.success(book)
        } catch (t: Throwable) {
            target.delete()
            coverFor(id).delete()
            Result.failure(t)
        }
    }

    suspend fun saveProgress(bookId: String, page: Int) {
        val current = book(bookId) ?: return
        if (current.lastPage == page) return
        update { books ->
            books.map {
                if (it.id == bookId) {
                    it.copy(lastPage = page.coerceIn(0, (it.pageCount - 1).coerceAtLeast(0)))
                } else {
                    it
                }
            }
        }
    }

    suspend fun markOpened(bookId: String) {
        update { books ->
            books.map {
                if (it.id == bookId) it.copy(lastOpenedAt = System.currentTimeMillis()) else it
            }
        }
    }

    suspend fun rename(bookId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        update { books -> books.map { if (it.id == bookId) it.copy(title = clean) else it } }
    }

    suspend fun resetProgress(bookId: String) {
        update { books -> books.map { if (it.id == bookId) it.copy(lastPage = 0) else it } }
    }

    suspend fun delete(bookId: String) {
        update { books -> books.filterNot { it.id == bookId } }
        withContext(Dispatchers.IO) {
            fileFor(bookId).delete()
            coverFor(bookId).delete()
        }
    }

    private suspend fun update(transform: (List<Book>) -> List<Book>) {
        ioLock.withLock {
            val next = transform(_books.value)
            _books.value = next
            withContext(Dispatchers.IO) { writeIndex(next) }
        }
    }

    private fun displayName(uri: Uri): String? {
        val name = runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

        return name?.removeSuffix(".pdf")?.removeSuffix(".PDF")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun writeCover(bookId: String, bitmap: Bitmap) {
        coversDir.mkdirs()
        coverFor(bookId).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
    }

    private fun readIndex(): List<Book> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            val result = ArrayList<Book>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.getString("id")
                if (!fileFor(id).exists()) continue
                result += Book(
                    id = id,
                    title = item.optString("title", "Libro sin titulo"),
                    pageCount = item.optInt("pageCount", 0),
                    lastPage = item.optInt("lastPage", 0),
                    addedAt = item.optLong("addedAt", 0L),
                    lastOpenedAt = item.optLong("lastOpenedAt", 0L)
                )
            }
            result.toList()
        }.getOrElse { emptyList() }
    }

    private fun writeIndex(books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("pageCount", book.pageCount)
                    .put("lastPage", book.lastPage)
                    .put("addedAt", book.addedAt)
                    .put("lastOpenedAt", book.lastOpenedAt)
            )
        }
        val tmp = File(indexFile.parentFile, "library.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(array.toString())
            tmp.delete()
        }
    }

    companion object {
        private const val COVER_WIDTH_PX = 420

        @Volatile
        private var instance: LibraryRepository? = null

        fun get(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(context).also { instance = it }
            }
    }
}
