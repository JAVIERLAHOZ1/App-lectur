package com.javierlahoz.lectur.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javierlahoz.lectur.data.Book
import com.javierlahoz.lectur.data.ThemeMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    isImporting: Boolean,
    themeMode: ThemeMode,
    snackbarHostState: SnackbarHostState,
    coverFor: (String) -> File,
    onAdd: () -> Unit,
    onOpen: (Book) -> Unit,
    onRename: (Book, String) -> Unit,
    onResetProgress: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onThemeMode: (ThemeMode) -> Unit
) {
    var renaming by remember { mutableStateOf<Book?>(null) }
    var deleting by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi biblioteca") },
                actions = { ThemeMenu(themeMode = themeMode, onThemeMode = onThemeMode) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                },
                text = { Text(if (isImporting) "Anadiendo..." else "Anadir PDF") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (books.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAdd = onAdd
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        coverFile = coverFor(book.id),
                        onOpen = { onOpen(book) },
                        onRename = { renaming = book },
                        onResetProgress = { onResetProgress(book) },
                        onDelete = { deleting = book }
                    )
                }
            }
        }
    }

    renaming?.let { book ->
        RenameDialog(
            book = book,
            onDismiss = { renaming = null },
            onConfirm = { newTitle ->
                onRename(book, newTitle)
                renaming = null
            }
        )
    }

    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar libro") },
            text = { Text("Se borrara \"${book.title}\" y su progreso de lectura. El archivo original de tu tablet no se toca.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(book)
                    deleting = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookCard(
    book: Book,
    coverFile: File,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column {
                BookCover(
                    coverFile = coverFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = progressLabel(book),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BookMenu(
                modifier = Modifier.align(Alignment.TopEnd),
                onRename = onRename,
                onResetProgress = onResetProgress,
                onDelete = onDelete
            )
        }
    }
}

private fun progressLabel(book: Book): String = when {
    book.pageCount <= 0 -> "PDF vacio"
    book.isFinished -> "Terminado - ${book.pageCount} pag."
    !book.isStarted -> "Sin empezar - ${book.pageCount} pag."
    else -> "Pag. ${book.lastPage + 1} de ${book.pageCount} - ${book.percent}%"
}

@Composable
private fun BookMenu(
    modifier: Modifier = Modifier,
    onRename: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Opciones del libro",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Renombrar") },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Empezar de nuevo") },
                onClick = {
                    expanded = false
                    onResetProgress()
                }
            )
            DropdownMenuItem(
                text = { Text("Eliminar") },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun ThemeMenu(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            val icon = when (themeMode) {
                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                ThemeMode.LIGHT -> Icons.Filled.LightMode
                ThemeMode.SEPIA -> Icons.Filled.LocalCafe
                ThemeMode.DARK -> Icons.Filled.DarkMode
            }
            Icon(icon, contentDescription = "Tema de la aplicacion")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Automatico (sistema)"
                                ThemeMode.LIGHT -> "Claro"
                                ThemeMode.SEPIA -> "Sepia"
                                ThemeMode.DARK -> "Oscuro"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.SEPIA -> Icons.Filled.LocalCafe
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        expanded = false
                        onThemeMode(mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(book: Book, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(book.id) { mutableStateOf(book.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar libro") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Titulo") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tu biblioteca esta vacia",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Anade el PDF que estas leyendo y la app recordara por que pagina vas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.clickable(onClick = onAdd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Anadir un PDF",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
