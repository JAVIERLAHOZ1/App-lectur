package com.javierlahoz.lectur

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.javierlahoz.lectur.data.ThemeMode
import com.javierlahoz.lectur.ui.LibraryScreen
import com.javierlahoz.lectur.ui.ReaderScreen
import com.javierlahoz.lectur.ui.theme.LecturTheme
import com.javierlahoz.lectur.ui.theme.pageTintOf
import com.javierlahoz.lectur.ui.theme.resolveTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // PdfBox necesita sus recursos (fuentes) antes de leer indices o texto.
        PDFBoxResourceLoader.init(applicationContext)
        handleViewIntent(intent)
        setContent { LecturApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /** "Abrir con -> Lectur" desde el gestor de archivos: lo guarda y lo abre. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        viewModel.importPdf(uri, openAfterwards = true)
    }
}

@Composable
private fun LecturApp(viewModel: LibraryViewModel) {
    val books by viewModel.books.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val readingMode by viewModel.readingMode.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val lockRotation by viewModel.lockRotation.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val message by viewModel.message.collectAsState()
    val openBookId by viewModel.openBookId.collectAsState()

    val resolvedTheme = resolveTheme(themeMode)
    val darkTheme = resolvedTheme == ThemeMode.DARK
    val snackbarHostState = remember { SnackbarHostState() }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    LecturTheme(theme = resolvedTheme) {
        val openBook = books.firstOrNull { it.id == openBookId }

        if (openBook != null) {
            BackHandler { viewModel.closeReader() }
            ReaderScreen(
                book = openBook,
                pdfFile = viewModel.pdfFile(openBook.id),
                theme = resolvedTheme,
                pageTint = pageTintOf(resolvedTheme),
                zoom = zoom,
                readingMode = readingMode,
                brightness = brightness,
                lockRotation = lockRotation,
                onZoomChange = viewModel::setZoom,
                onReadingModeChange = viewModel::setReadingMode,
                onThemeChange = viewModel::setThemeMode,
                onBrightnessChange = viewModel::setBrightness,
                onLockRotationChange = viewModel::setLockRotation,
                onProgress = { page -> viewModel.saveProgress(openBook.id, page) },
                onBack = viewModel::closeReader
            )
        } else {
            LibraryScreen(
                books = books,
                isImporting = isImporting,
                themeMode = themeMode,
                snackbarHostState = snackbarHostState,
                coverFor = { id -> viewModel.coverFile(id) },
                onAdd = { pickPdf.launch(arrayOf("application/pdf")) },
                onOpen = { book -> viewModel.open(book.id) },
                onRename = { book, title -> viewModel.rename(book.id, title) },
                onResetProgress = { book -> viewModel.resetProgress(book.id) },
                onDelete = { book -> viewModel.delete(book.id) },
                onThemeMode = viewModel::setThemeMode
            )
        }
    }
}
