package com.javierlahoz.lectur.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Toc
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.javierlahoz.lectur.data.Book
import com.javierlahoz.lectur.data.ReadingMode
import com.javierlahoz.lectur.data.SettingsStore
import com.javierlahoz.lectur.data.ThemeMode
import com.javierlahoz.lectur.dict.DictionaryClient
import com.javierlahoz.lectur.pdf.OutlineEntry
import com.javierlahoz.lectur.pdf.PdfLoadState
import com.javierlahoz.lectur.pdf.PdfPageLoader
import com.javierlahoz.lectur.pdf.PdfTextIndex
import com.javierlahoz.lectur.pdf.cleanWord
import com.javierlahoz.lectur.pdf.rememberPdfDocument
import com.javierlahoz.lectur.ui.theme.PageTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Invierte los colores: fondo negro y letras blancas. */
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

/**
 * Lleva el blanco a papel crema (#F5ECD7) y el negro a tinta marron (#3B2F2F),
 * pasando por la luminosidad de cada pixel.
 */
private val SepiaColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.218f, 0.428f, 0.083f, 0f, 59f,
            0.222f, 0.435f, 0.084f, 0f, 47f,
            0.197f, 0.387f, 0.075f, 0f, 47f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private fun filterFor(tint: PageTint): ColorFilter? = when (tint) {
    PageTint.NONE -> null
    PageTint.SEPIA -> SepiaColorFilter
    PageTint.INVERT -> InvertColorFilter
}

@Composable
fun ReaderScreen(
    book: Book,
    pdfFile: File,
    theme: ThemeMode,
    pageTint: PageTint,
    zoom: Float,
    readingMode: ReadingMode,
    brightness: Float,
    lockRotation: Boolean,
    onZoomChange: (Float) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onLockRotationChange: (Boolean) -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    val loadState by rememberPdfDocument(pdfFile)
    val activity = LocalContext.current.findActivity()

    // Mantiene la pantalla encendida mientras se lee.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Brillo propio del lector, sin tocar el del sistema.
    DisposableEffect(activity, brightness) {
        activity?.applyBrightness(brightness)
        onDispose { activity?.applyBrightness(SettingsStore.SYSTEM_BRIGHTNESS) }
    }

    // Bloqueo de rotacion mientras se lee.
    DisposableEffect(activity, lockRotation) {
        activity?.requestedOrientation = if (lockRotation) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
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
                    pdfFile = pdfFile,
                    loader = state.loader,
                    theme = theme,
                    pageTint = pageTint,
                    zoom = zoom,
                    readingMode = readingMode,
                    brightness = brightness,
                    lockRotation = lockRotation,
                    background = background,
                    onZoomChange = onZoomChange,
                    onReadingModeChange = onReadingModeChange,
                    onThemeChange = onThemeChange,
                    onBrightnessChange = onBrightnessChange,
                    onLockRotationChange = onLockRotationChange,
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
    pdfFile: File,
    loader: PdfPageLoader,
    theme: ThemeMode,
    pageTint: PageTint,
    zoom: Float,
    readingMode: ReadingMode,
    brightness: Float,
    lockRotation: Boolean,
    background: Color,
    onZoomChange: (Float) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onLockRotationChange: (Boolean) -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit
) {
    val pageCount = loader.pageCount
    val lastIndex = (pageCount - 1).coerceAtLeast(0)
    val startPage = remember(book.id, pageCount) { book.lastPage.coerceIn(0, lastIndex) }

    var chromeVisible by remember { mutableStateOf(true) }
    var showBrightness by remember { mutableStateOf(false) }
    var currentPage by remember(book.id) { mutableStateOf(startPage) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val textIndex = rememberPdfTextIndex(pdfFile)
    val pageBounds = remember(book.id) { PageBounds() }

    var outlineVisible by remember { mutableStateOf(false) }
    var outlineEntries by remember(book.id) { mutableStateOf<List<OutlineEntry>?>(null) }
    var lookup by remember { mutableStateOf<LookupState>(LookupState.Hidden) }

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

        val goToPage: (Int) -> Unit = { page ->
            val target = page.coerceIn(0, lastIndex)
            currentPage = target
            scope.launch {
                if (effectiveMode == ReadingMode.SCROLL) {
                    listState.scrollToItem(target)
                } else {
                    pagerState.scrollToPage((target / pagesPerSpread).coerceIn(0, spreadCount - 1))
                }
            }
        }

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

        // Mantener pulsada una palabra: la busca en el diccionario.
        val lookUpWord: (Offset) -> Unit = { localOffset ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val hit = pageBounds.locate(localOffset)
            if (hit == null) {
                lookup = LookupState.NoWord
            } else {
                lookup = LookupState.Searching("")
                scope.launch {
                    val raw = textIndex.wordAt(hit.pageIndex, hit.fractionX, hit.fractionY)
                    val word = raw?.let { cleanWord(it) }.orEmpty()
                    if (word.isEmpty()) {
                        lookup = LookupState.NoWord
                    } else {
                        lookup = LookupState.Searching(word)
                        lookup = LookupState.Ready(DictionaryClient.define(word))
                    }
                }
            }
        }

        when (effectiveMode) {
            ReadingMode.SCROLL -> ScrollReader(
                loader = loader,
                pageCount = pageCount,
                listState = listState,
                zoom = zoom,
                pageTint = pageTint,
                background = background,
                viewportWidth = maxWidth,
                pageBounds = pageBounds,
                onTapCenter = toggleChrome,
                onLongPress = lookUpWord
            )

            ReadingMode.PAGE, ReadingMode.BOOK -> PagedReader(
                loader = loader,
                pageCount = pageCount,
                pagesPerSpread = pagesPerSpread,
                pagerState = pagerState,
                pageTint = pageTint,
                background = background,
                pageBounds = pageBounds,
                scope = scope,
                onTapCenter = toggleChrome,
                onLongPress = lookUpWord
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
                theme = theme,
                onThemeChange = onThemeChange,
                onOpenOutline = {
                    outlineVisible = true
                    if (outlineEntries == null) {
                        scope.launch { outlineEntries = textIndex.outline() }
                    }
                },
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
                brightness = brightness,
                lockRotation = lockRotation,
                showZoom = effectiveMode == ReadingMode.SCROLL,
                showBrightness = showBrightness,
                onToggleBrightness = { showBrightness = !showBrightness },
                onBrightnessChange = onBrightnessChange,
                onLockRotationChange = onLockRotationChange,
                onZoomChange = onZoomChange,
                onReadingModeChange = onReadingModeChange,
                onSeek = goToPage
            )
        }

        if (outlineVisible) {
            OutlineSheet(
                entries = outlineEntries,
                pageCount = pageCount,
                onSelect = { page ->
                    outlineVisible = false
                    goToPage(page)
                },
                onDismiss = { outlineVisible = false }
            )
        }

        DictionarySheet(
            state = lookup,
            onDismiss = { lookup = LookupState.Hidden },
            onSearchOnWeb = { word ->
                lookup = LookupState.Hidden
                context.openRae(word)
            }
        )
    }
}

/** Scroll vertical continuo, con zoom y desplazamiento lateral cuando se amplia. */
@Composable
private fun ScrollReader(
    loader: PdfPageLoader,
    pageCount: Int,
    listState: LazyListState,
    zoom: Float,
    pageTint: PageTint,
    background: Color,
    viewportWidth: Dp,
    pageBounds: PageBounds,
    onTapCenter: () -> Unit,
    onLongPress: (Offset) -> Unit
) {
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val contentWidth = viewportWidth * zoom
    val renderWidthPx = with(density) { contentWidth.toPx() }.roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { pageBounds.origin = it.positionInRoot() }
            .horizontalScroll(horizontalScroll)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapCenter() },
                    onLongPress = onLongPress
                )
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
                    pageTint = pageTint,
                    background = background,
                    pageBounds = pageBounds,
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
    pageTint: PageTint,
    background: Color,
    pageBounds: PageBounds,
    scope: CoroutineScope,
    onTapCenter: () -> Unit,
    onLongPress: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { pageBounds.origin = it.positionInRoot() }
            .pointerInput(pagerState, pagesPerSpread) {
                detectTapGestures(
                    onLongPress = onLongPress,
                    onTap = { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.25f -> scope.launch {
                                val target = pagerState.currentPage - 1
                                if (target >= 0) pagerState.animateScrollToPage(target)
                            }

                            offset.x > width * 0.75f -> scope.launch {
                                val target = pagerState.currentPage + 1
                                if (target < pagerState.pageCount) {
                                    pagerState.animateScrollToPage(target)
                                }
                            }

                            else -> onTapCenter()
                        }
                    }
                )
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
                            pageTint = pageTint,
                            background = background,
                            pageBounds = pageBounds,
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
    pageTint: PageTint,
    background: Color,
    pageBounds: PageBounds,
    modifier: Modifier = Modifier
) {
    var image by remember(index, widthPx) { mutableStateOf<ImageBitmap?>(null) }
    var ratio by remember(index) { mutableStateOf(1.414f) }

    LaunchedEffect(index, widthPx) {
        ratio = loader.aspectRatio(index)
        image = loader.page(index, widthPx)?.asImageBitmap()
    }

    DisposableEffect(index) {
        onDispose { pageBounds.forget(index) }
    }

    PageSurface(
        image = image,
        pageTint = pageTint,
        contentDescription = "Pagina ${index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .aspectRatio(1f / ratio.coerceAtLeast(0.1f))
            .background(background)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                pageBounds.putBox(
                    index,
                    Rect(
                        left = position.x,
                        top = position.y,
                        right = position.x + coordinates.size.width,
                        bottom = position.y + coordinates.size.height
                    )
                )
            }
    )
}

/** Pagina completa ajustada al hueco disponible (modo pagina y modo libro). */
@Composable
private fun FittedPage(
    index: Int,
    loader: PdfPageLoader,
    pageTint: PageTint,
    background: Color,
    pageBounds: PageBounds,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.background(background)) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }
        var ratio by remember(index) { mutableStateOf(1.414f) }

        LaunchedEffect(index, availableWidthPx, availableHeightPx) {
            val pageRatio = loader.aspectRatio(index).coerceAtLeast(0.1f)
            ratio = pageRatio
            // La pagina entera tiene que caber: se limita por ancho y por alto.
            val width = min(availableWidthPx, availableHeightPx / pageRatio).roundToInt()
            image = loader.page(index, width)?.asImageBitmap()
        }

        DisposableEffect(index) {
            onDispose { pageBounds.forget(index) }
        }

        // La proporcion llega despues de leer la pagina, asi que se registra aparte.
        LaunchedEffect(index, ratio) {
            pageBounds.putFittedRatio(index, ratio)
        }

        PageSurface(
            image = image,
            pageTint = pageTint,
            contentDescription = "Pagina ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    pageBounds.putBox(
                        index,
                        Rect(
                            left = position.x,
                            top = position.y,
                            right = position.x + coordinates.size.width,
                            bottom = position.y + coordinates.size.height
                        )
                    )
                }
        )
    }
}

@Composable
private fun PageSurface(
    image: ImageBitmap?,
    pageTint: PageTint,
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
                colorFilter = filterFor(pageTint)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = if (pageTint == PageTint.INVERT) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenOutline: () -> Unit,
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
            IconButton(onClick = onOpenOutline) {
                Icon(
                    Icons.Filled.Toc,
                    contentDescription = "Indice del libro",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            ReaderThemeMenu(theme = theme, onThemeChange = onThemeChange)
        }
    }
}

@Composable
private fun ReaderThemeMenu(theme: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (theme) {
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                    ThemeMode.SEPIA -> Icons.Filled.LocalCafe
                    else -> Icons.Filled.LightMode
                },
                contentDescription = "Aspecto de la pagina",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Claro") },
                leadingIcon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
                onClick = {
                    expanded = false
                    onThemeChange(ThemeMode.LIGHT)
                }
            )
            DropdownMenuItem(
                text = { Text("Sepia") },
                leadingIcon = { Icon(Icons.Filled.LocalCafe, contentDescription = null) },
                onClick = {
                    expanded = false
                    onThemeChange(ThemeMode.SEPIA)
                }
            )
            DropdownMenuItem(
                text = { Text("Oscuro") },
                leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                onClick = {
                    expanded = false
                    onThemeChange(ThemeMode.DARK)
                }
            )
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
    brightness: Float,
    lockRotation: Boolean,
    showZoom: Boolean,
    showBrightness: Boolean,
    onToggleBrightness: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onLockRotationChange: (Boolean) -> Unit,
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
                    IconButton(onClick = onToggleBrightness) {
                        Icon(
                            Icons.Filled.BrightnessMedium,
                            contentDescription = "Brillo del lector",
                            tint = if (showBrightness) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { onLockRotationChange(!lockRotation) }) {
                        Icon(
                            imageVector = if (lockRotation) {
                                Icons.Filled.ScreenLockRotation
                            } else {
                                Icons.Filled.ScreenRotation
                            },
                            contentDescription = if (lockRotation) {
                                "Desbloquear rotacion"
                            } else {
                                "Bloquear rotacion"
                            },
                            tint = if (lockRotation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    ReadingModeMenu(
                        readingMode = readingMode,
                        onReadingModeChange = onReadingModeChange
                    )
                }
            }

            if (showBrightness) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.BrightnessMedium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = if (brightness < 0f) 1f else brightness,
                        onValueChange = onBrightnessChange,
                        valueRange = SettingsStore.MIN_BRIGHTNESS..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    TextButton(
                        onClick = { onBrightnessChange(SettingsStore.SYSTEM_BRIGHTNESS) },
                        enabled = brightness >= 0f
                    ) {
                        Text("Auto")
                    }
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

/** Donde esta dibujada cada pagina, para saber que palabra hay bajo el dedo. */
private class PageBounds {
    /** Hueco que ocupa cada pagina en pantalla. */
    private val boxes = HashMap<Int, Rect>()

    /**
     * Solo en los modos paginados: proporcion de la pagina, para descontar las
     * bandas que quedan a los lados o arriba cuando la pagina se centra.
     */
    private val fittedRatios = HashMap<Int, Float>()

    var origin: Offset = Offset.Zero

    fun putBox(index: Int, rect: Rect) {
        boxes[index] = rect
    }

    fun putFittedRatio(index: Int, ratio: Float) {
        fittedRatios[index] = ratio
    }

    fun forget(index: Int) {
        boxes.remove(index)
        fittedRatios.remove(index)
    }

    fun locate(local: Offset): PageHit? {
        val point = local + origin
        for ((index, box) in boxes) {
            if (box.width <= 0f || box.height <= 0f) continue
            val rect = fittedRatios[index]?.let { fittedRect(box, it) } ?: box
            if (rect.width > 0f && rect.height > 0f && rect.contains(point)) {
                return PageHit(
                    pageIndex = index,
                    fractionX = ((point.x - rect.left) / rect.width).coerceIn(0f, 1f),
                    fractionY = ((point.y - rect.top) / rect.height).coerceIn(0f, 1f)
                )
            }
        }
        return null
    }

    /** La pagina se dibuja centrada y entera dentro de su hueco. */
    private fun fittedRect(box: Rect, ratio: Float): Rect {
        val safeRatio = ratio.coerceAtLeast(0.1f)
        val width = min(box.width, box.height / safeRatio)
        val height = width * safeRatio
        val left = box.left + (box.width - width) / 2f
        val top = box.top + (box.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }
}

private data class PageHit(val pageIndex: Int, val fractionX: Float, val fractionY: Float)

@Composable
private fun rememberPdfTextIndex(file: File): PdfTextIndex {
    val index = remember(file.absolutePath) { PdfTextIndex(file) }
    DisposableEffect(index) {
        onDispose { index.close() }
    }
    return index
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Activity.applyBrightness(value: Float) {
    val params = window?.attributes ?: return
    params.screenBrightness = if (value < 0f) {
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        value.coerceIn(0.01f, 1f)
    }
    window?.attributes = params
}

private fun Context.openRae(word: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://dle.rae.es/${android.net.Uri.encode(word)}")
    )
    runCatching { startActivity(intent) }
}
