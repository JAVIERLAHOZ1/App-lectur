package com.javierlahoz.lectur.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import com.javierlahoz.lectur.data.Book
import com.javierlahoz.lectur.data.SettingsStore
import com.javierlahoz.lectur.pdf.PdfLoadState
import com.javierlahoz.lectur.pdf.PdfPageLoader
import com.javierlahoz.lectur.pdf.rememberPdfDocument
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** Filtro que invierte los colores: fondo negro y letras blancas al leer de noche. */
private val InvertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
fun ReaderScreen(
    book: Book,
    pdfFile: File,
    darkReading: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onToggleDarkReading: () -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit
) {
    val background = if (darkReading) Color.Black else MaterialTheme.colorScheme.background
    val foreground = if (darkReading) Color.White else MaterialTheme.colorScheme.onBackground
    val loadState by rememberPdfDocument(pdfFile)

    // Mantiene la pantalla encendida mientras se lee.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        when (val state = loadState) {
            is PdfLoadState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = foreground
                )
            }

            is PdfLoadState.Failed -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No se ha podido abrir este PDF",
                        style = MaterialTheme.typography.titleMedium,
                        color = foreground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = state.message, color = foreground)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onBack) { Text("Volver a la biblioteca") }
                }
            }

            is PdfLoadState.Ready -> {
                ReaderContent(
                    book = book,
                    loader = state.loader,
                    darkReading = darkReading,
                    zoom = zoom,
                    background = background,
                    foreground = foreground,
                    onZoomChange = onZoomChange,
                    onToggleDarkReading = onToggleDarkReading,
                    onProgress = onProgress,
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun ReaderContent(
    book: Book,
    loader: PdfPageLoader,
    darkReading: Boolean,
    zoom: Float,
    background: Color,
    foreground: Color,
    onZoomChange: (Float) -> Unit,
    onToggleDarkReading: () -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit
) {
    val pageCount = loader.pageCount
    val startPage = remember(book.id, pageCount) {
        book.lastPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var currentPage by remember(book.id) { mutableStateOf(startPage) }

    // Guarda el progreso segun se avanza y tambien al salir del lector.
    LaunchedEffect(listState, book.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                currentPage = index
                onProgress(index)
            }
    }
    DisposableEffect(book.id) {
        onDispose { onProgress(listState.firstVisibleItemIndex) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidth = maxWidth
        val contentWidth = viewportWidth * zoom
        val renderWidthPx = with(density) { contentWidth.toPx() }.roundToInt()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(contentWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                items(count = pageCount) { index ->
                    PdfPageView(
                        index = index,
                        loader = loader,
                        widthPx = renderWidthPx,
                        darkReading = darkReading,
                        background = background,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp).fillMaxWidth().background(background))
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book.title,
                darkReading = darkReading,
                onToggleDarkReading = onToggleDarkReading,
                onBack = onBack
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                currentPage = currentPage,
                pageCount = pageCount,
                zoom = zoom,
                onZoomChange = onZoomChange,
                onSeek = { page ->
                    currentPage = page
                    scope.launch { listState.scrollToItem(page) }
                }
            )
        }
    }
}

@Composable
private fun PdfPageView(
    index: Int,
    loader: PdfPageLoader,
    widthPx: Int,
    darkReading: Boolean,
    background: Color,
    modifier: Modifier = Modifier
) {
    var image by remember(index, widthPx) { mutableStateOf<ImageBitmap?>(null) }
    var ratio by remember(index) { mutableStateOf(1.414f) }

    LaunchedEffect(index, widthPx) {
        ratio = loader.aspectRatio(index)
        image = loader.page(index, widthPx)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f / ratio.coerceAtLeast(0.1f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Pagina ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
                colorFilter = if (darkReading) InvertColorFilter else null
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = if (darkReading) Color.White else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    darkReading: Boolean,
    onToggleDarkReading: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a la biblioteca",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onToggleDarkReading) {
                Icon(
                    imageVector = if (darkReading) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Cambiar modo oscuro",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onSeek: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pagina ${currentPage + 1} de $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onZoomChange(zoom - SettingsStore.ZOOM_STEP) },
                        enabled = zoom > SettingsStore.MIN_ZOOM
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "Reducir",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { onZoomChange(zoom + SettingsStore.ZOOM_STEP) },
                        enabled = zoom < SettingsStore.MAX_ZOOM
                    ) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "Ampliar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (pageCount > 1) {
                Slider(
                    value = currentPage.toFloat().coerceIn(0f, (pageCount - 1).toFloat()),
                    onValueChange = { value -> onSeek(value.roundToInt().coerceIn(0, pageCount - 1)) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
