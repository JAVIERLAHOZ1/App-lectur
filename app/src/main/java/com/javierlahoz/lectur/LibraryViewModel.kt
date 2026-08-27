package com.javierlahoz.lectur

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierlahoz.lectur.data.Book
import com.javierlahoz.lectur.data.LibraryRepository
import com.javierlahoz.lectur.data.ReadingMode
import com.javierlahoz.lectur.data.SettingsStore
import com.javierlahoz.lectur.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LibraryRepository.get(application)
    private val settings = SettingsStore.get(application)

    /** Los mas recientes primero: primero lo ultimo abierto, luego lo ultimo anadido. */
    val books: StateFlow<List<Book>> = repository.books
        .map { list -> list.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val readingMode: StateFlow<ReadingMode> = settings.readingMode
    val zoom: StateFlow<Float> = settings.zoom
    val brightness: StateFlow<Float> = settings.brightness
    val lockRotation: StateFlow<Boolean> = settings.lockRotation

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _openBookId = MutableStateFlow<String?>(null)
    val openBookId: StateFlow<String?> = _openBookId.asStateFlow()

    init {
        viewModelScope.launch { repository.loadIfNeeded() }
    }

    fun bookById(id: String?): Book? = repository.book(id)

    fun pdfFile(bookId: String) = repository.fileFor(bookId)

    fun coverFile(bookId: String) = repository.coverFor(bookId)

    fun importPdf(uri: Uri, openAfterwards: Boolean = false) {
        viewModelScope.launch {
            _isImporting.value = true
            repository.loadIfNeeded()
            val result = repository.import(uri)
            _isImporting.value = false
            result
                .onSuccess { book ->
                    _message.value = "Anadido: ${book.title}"
                    if (openAfterwards) open(book.id)
                }
                .onFailure {
                    _message.value = "No se ha podido anadir el PDF (${it.message ?: "error desconocido"})"
                }
        }
    }

    fun open(bookId: String) {
        _openBookId.value = bookId
        viewModelScope.launch { repository.markOpened(bookId) }
    }

    fun closeReader() {
        _openBookId.value = null
    }

    fun saveProgress(bookId: String, page: Int) {
        viewModelScope.launch { repository.saveProgress(bookId, page) }
    }

    fun rename(bookId: String, title: String) {
        viewModelScope.launch { repository.rename(bookId, title) }
    }

    fun resetProgress(bookId: String) {
        viewModelScope.launch { repository.resetProgress(bookId) }
    }

    fun delete(bookId: String) {
        if (_openBookId.value == bookId) _openBookId.value = null
        viewModelScope.launch { repository.delete(bookId) }
    }

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setReadingMode(mode: ReadingMode) = settings.setReadingMode(mode)

    fun setBrightness(value: Float) = settings.setBrightness(value)

    fun setLockRotation(locked: Boolean) = settings.setLockRotation(locked)

    fun setZoom(value: Float) = settings.setZoom(value)

    fun consumeMessage() {
        _message.value = null
    }
}
