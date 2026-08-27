package com.javierlahoz.lectur.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.javierlahoz.lectur.data.Book
import com.javierlahoz.lectur.data.ReadingMode
import com.javierlahoz.lectur.data.SettingsStore
import com.javierlahoz.lectur.pdf.PdfLoadState
import com.javierlahoz.lectur.pdf.PdfPageLoader
import com.javierlahoz.lectur.pdf.rememberPdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min
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
    readingMode: ReadingMode,
    onZoomChange: (Float) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
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
                    readingMode = readingMode,
                    background = background,
                    onZoomChange = onZoomChange,
                    onReadingModeChange = onReadingModeChange,
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
    readingMode: ReadingMode,
    background: Color,
    onZoomChange: (Float) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onToggleDarkReading: () -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit
) {
    val pageCount = loader.pageCount
    val lastIndex = (pageCount - 1).coerceAtLeast(0)
    val startPage = remember(book.id, pageCount) { book.lastPage.coerceIn(0, lastIndex) }

    var chromeVisible by remember { mutableStateOf(true) }
    var currentPage by remember(book.id) { mutableStateOf(startPage) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // El modo libro (dos paginas) solo tiene sentido con la tablet apaisada.
        val landscape = maxWidth > maxHeight
        val effectiveMode = if (readingMode == ReadingMode.BOOK && !landscape) {
            ReadingMode.PAGE
        } else {
            readingMode
        }
        val pagesPerSpread = if (effectiveMode == ReadingMode.BOOK) 2 else 1
        val spreadCount = ((pageCount + pagesPerSpread - 1) / pagesPerSpread).coerceAtLeast(1)

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
        val pagerState = rememberPagerState(
            initialPage = (startPage / pagesPerSpread).coerceIn(0, spreadCount - 1),
            pageCount = { spreadCount }
        )

        // Al cambiar de modo se recoloca en la pagina actual y se sigue guardando el progreso.
        LaunchedEffect(effectiveMode, book.id, pageCount) {
            val page = currentPage.coerceIn(0, lastIndex)
            if (effectiveMode == ReadingMode.SCROLL) {
                listState.scrollToItem(page)
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { index ->
                        currentPage = index
                        onProgress(index)
                    }
            } else {
                pagerState.scrollToPage((page / pagesPerSpread).coerceIn(0, spreadCount - 1))
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { spread ->
                        val index = (spread * pagesPerSpread).coerceIn(0, lastIndex)
                        currentPage = index
                        onProgress(index)
                    }
            }
        }

        DisposableEffect(book.id) {
            onDispose { onProgress(currentPage) }
        }

        val toggleChrome = { chromeVisible = !chromeVisible }

        when (effectiveMode) {
            ReadingMode.SCROLL -> ScrollReader(
                loader = loader,
                pageCount = pageCount,
                listState = listState,
                zoom = zoom,
                darkReading = darkReading,
                background = background,
                viewportWidth = maxWidth,
                onTapCenter = toggleChrome
            )

            ReadingMode.PAGE, ReadingMode.BOOK -> PagedReader(
                loader = loader,
                pageCount = pageCount,
                pagesPerSpread = pagesPerSpread,
                pagerState = pagerState,
                darkReading = darkReading,
                background = background,
                scope = scope,
                onTapCenter = toggleChrome
            )
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
                pagesPerSpread = pagesPerSpread,
                zoom = zoom,
                readingMode = readingMode,
                showZoom = effectiveMode == ReadingMode.SCROLL,
                onZoomChange = onZoomChange,
                onReadingModeChange = onReadingModeChange,
                onSeek = { page ->
                    val target = page.coerceIn(0, lastIndex)
                    currentPage = target
                    scope.launch {
                        if (effectiveMode == ReadingMode.SCROLL) {
                            listState.scrollToItem(target)
                        } else {
                            pagerState.scrollToPage(
                                (target / pagesPerSpread).coerceIn(0, spreadCount - 1)
                            )
                        }
                    }
                }
            )
        }
    }
}

/** Scroll vertical continuo, con zoom y desplazamiento lateral cuando se amplia. */
@Composable
private fun ScrollReader(
    loader: PdfPageLoader,
    pageCount: Int,
    listState: LazyListState,
    zoom: Float,
    darkReading: Boolean,
    background: Color,
    viewportWidth: androidx.compose.ui.unit.Dp,
    onTapCenter: () -> Unit
) {
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val contentWidth = viewportWidth * zoom
    val renderWidthPx = with(density) { contentWidth.toPx() }.roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapCenter() })
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.width(contentWidth).fillMaxHeight()
        ) {
            items(count = pageCount) { index ->
                ScrollPage(
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
}

/**
 * Modo pagina y modo libro: se pasa de pagina deslizando de lado, o tocando
 * el borde derecho/izquierdo de la pantalla. En modo libro se ven dos paginas
 * abiertas, como un libro de papel.
 */
@Composable
private fun PagedReader(
    loader: PdfPageLoader,
    pageCount: Int,
    pagesPerSpread: Int,
    pagerState: PagerState,
    darkReading: Boolean,
    background: Color,
    scope: CoroutineScope,
    onTapCenter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pagerState, pagesPerSpread) {
                detectTapGestures { offset ->
                    val width = size.width
                    when {
                        offset.x < width * 0.25f -> scope.launch {
                            val target = pagerState.currentPage - 1
                            if (target >= 0) pagerState.animateScrollToPage(target)
                        }

                        offset.x > width * 0.75f -> scope.launch {
                            val target = pagerState.currentPage + 1
                            if (target < pagerState.pageCount) pagerState.animateScrollToPage(target)
                        }

                        else -> onTapCenter()
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { spreadIndex ->
            Row(modifier = Modifier.fillMaxSize().background(background)) {
                repeat(pagesPerSpread) { slot ->
                    val pageIndex = spreadIndex * pagesPerSpread + slot
                    if (pageIndex < pageCount) {
                        FittedPage(
                            index = pageIndex,
                            loader = loader,
                            darkReading = darkReading,
                            background = background,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        // Ultima pagina impar en modo libro: hueco en blanco a la derecha.
                        Spacer(Modifier.weight(1f).fillMaxHeight().background(background))
                    }
                }
            }
        }
    }
}

/** Pagina dibujada al ancho del contenedor (scroll continuo). */
@Composable
private fun ScrollPage(
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

    PageSurface(
        image = image,
        darkReading = darkReading,
        contentDescription = "Pagina ${index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = modifier.aspectRatio(1f / ratio.coerceAtLeast(0.1f)).background(background)
    )
}

/** Pagina completa ajustada al hueco disponible (modo pagina y modo libro). */
@Composable
private fun FittedPage(
    index: Int,
    loader: PdfPageLoader,
    darkReading: Boolean,
    background: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.background(background)) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(index, availableWidthPx, availableHeightPx) {
            val ratio = loader.aspectRatio(index).coerceAtLeast(0.1f)
            // La pagina entera tiene que caber: se limita por ancho y por alto.
            val width = min(availableWidthPx, availableHeightPx / ratio).roundToInt()
            image = loader.page(index, width)?.asImageBitmap()
        }

        PageSurface(
            image = image,
            darkReading = darkReading,
            contentDescription = "Pagina ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PageSurface(
    image: ImageBitmap?,
    darkReading: Boolean,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
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
    pagesPerSpread: Int,
    zoom: Float,
    readingMode: ReadingMode,
    showZoom: Boolean,
    onZoomChange: (Float) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
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
                    text = pageLabel(currentPage, pageCount, pagesPerSpread),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showZoom) {
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
                    ReadingModeMenu(
                        readingMode = readingMode,
                        onReadingModeChange = onReadingModeChange
                    )
                }
            }

            if (pageCount > 1) {
                Slider(
                    value = currentPage.toFloat().coerceIn(0f, (pageCount - 1).toFloat()),
                    onValueChange = { value -> onSeek(value.roundToInt()) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReadingModeMenu(
    readingMode: ReadingMode,
    onReadingModeChange: (ReadingMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (readingMode) {
                    ReadingMode.SCROLL -> Icons.Filled.ViewDay
                    ReadingMode.PAGE -> Icons.Filled.Description
                    ReadingMode.BOOK -> Icons.Filled.MenuBook
                },
                contentDescription = "Modo de lectura",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Scroll continuo") },
                leadingIcon = { Icon(Icons.Filled.ViewDay, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReadingModeChange(ReadingMode.SCROLL)
                }
            )
            DropdownMenuItem(
                text = { Text("Pagina a pagina") },
                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReadingModeChange(ReadingMode.PAGE)
                }
            )
            DropdownMenuItem(
                text = { Text("Libro (dos paginas)") },
                leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReadingModeChange(ReadingMode.BOOK)
                }
            )
        }
    }
}

private fun pageLabel(currentPage: Int, pageCount: Int, pagesPerSpread: Int): String {
    if (pagesPerSpread > 1 && currentPage + 1 < pageCount) {
        return "Paginas ${currentPage + 1}-${currentPage + 2} de $pageCount"
    }
    return "Pagina ${currentPage + 1} de $pageCount"
}
