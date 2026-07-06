package com.virtualworkspace.presentation.browser

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.presentation.common.ConfirmDialog
import com.virtualworkspace.presentation.common.FolderPickerDialog
import com.virtualworkspace.presentation.common.TextInputDialog

private sealed interface BrowserDialog {
    data object CreateFolder : BrowserDialog
    data class Rename(val nodeId: Long, val currentName: String) : BrowserDialog
    data class Move(val nodeId: Long) : BrowserDialog
    data class ConfirmTrash(val nodeId: Long, val name: String) : BrowserDialog
    data class ConfirmRemoveRef(val nodeId: Long, val name: String) : BrowserDialog
    data class Consolidate(val nodeId: Long) : BrowserDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val children by viewModel.children.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val message by viewModel.message.collectAsState()
    val properties by viewModel.properties.collectAsState()

    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Pasta pendente para consolidação (aguarda o picker SAF de destino)
    var consolidateTargetNode by remember { mutableStateOf<Long?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.addFileReference(uri.toString())
    }

    val mapFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isFailure) {
            viewModel.reportError("Não foi possível manter acesso à pasta selecionada")
            return@rememberLauncherForActivityResult
        }
        val displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Pasta"
        viewModel.mapFolder(uri.toString(), flags, displayName)
    }

    val consolidatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val nodeId = consolidateTargetNode
        consolidateTargetNode = null
        if (uri != null && nodeId != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.isSuccess
            if (!granted) {
                viewModel.reportError("Não foi possível manter acesso à pasta de destino")
                return@rememberLauncherForActivityResult
            }
            viewModel.consolidate(nodeId, uri.toString())
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = breadcrumbs.isNotEmpty()) { viewModel.goUp() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(breadcrumbs.lastOrNull()?.name ?: "Workspace") },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.goUp()) onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Pesquisar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { fabMenuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar")
                DropdownMenu(expanded = fabMenuOpen, onDismissRequest = { fabMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nova pasta virtual") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        onClick = {
                            fabMenuOpen = false
                            dialog = BrowserDialog.CreateFolder
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar ficheiro (referência)") },
                        leadingIcon = { Icon(Icons.Filled.Link, null) },
                        onClick = {
                            fabMenuOpen = false
                            filePicker.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mapear pasta física (SAF)") },
                        leadingIcon = { Icon(Icons.Filled.Folder, null) },
                        onClick = {
                            fabMenuOpen = false
                            mapFolderPicker.launch(null)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Breadcrumbs(
                path = breadcrumbs.map { it.name },
                onCrumbClick = { viewModel.navigateToBreadcrumb(it) }
            )

            if (breadcrumbs.isEmpty() && pinned.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pinned.forEach { node ->
                        AssistChip(
                            onClick = { viewModel.openPinned(node) },
                            label = { Text(node.name) },
                            leadingIcon = { Icon(Icons.Filled.PushPin, null) }
                        )
                    }
                }
            }

            if (children.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Pasta vazia", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Crie pastas virtuais ou adicione referências a ficheiros com o botão +",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(children, key = { it.node.id }) { item ->
                        NodeRow(
                            item = item,
                            onOpen = {
                                if (item.node.type == NodeType.FILE_REFERENCE) {
                                    val uri = DocumentsContract.buildDocumentUri(
                                        "com.virtualworkspace.documents", "n${item.node.id}"
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, item.storageObject?.mimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure { viewModel.reportError("Nenhuma aplicação consegue abrir este ficheiro") }
                                } else viewModel.openFolder(item.node)
                            },
                            onRename = { dialog = BrowserDialog.Rename(item.node.id, item.node.name) },
                            onMove = { dialog = BrowserDialog.Move(item.node.id) },
                            onTrash = { dialog = BrowserDialog.ConfirmTrash(item.node.id, item.node.name) },
                            onRemoveRef = { dialog = BrowserDialog.ConfirmRemoveRef(item.node.id, item.node.name) },
                            onConsolidate = { dialog = BrowserDialog.Consolidate(item.node.id) },
                            onPin = { viewModel.setPinned(item.node.id, !item.node.isPinned) },
                            onProperties = { viewModel.showProperties(item.node.id) }
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is BrowserDialog.CreateFolder -> TextInputDialog(
            title = "Nova pasta virtual",
            label = "Nome",
            confirmLabel = "Criar",
            onConfirm = { name ->
                viewModel.createFolder(name)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        is BrowserDialog.Rename -> TextInputDialog(
            title = "Renomear",
            label = "Novo nome",
            initialValue = d.currentName,
            onConfirm = { name ->
                viewModel.rename(d.nodeId, name)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        is BrowserDialog.Move -> FolderPickerDialog(
            title = "Mover para…",
            loadFolders = { parentId -> viewModel.getFolders(parentId) },
            excludeNodeId = d.nodeId,
            onSelect = { targetId ->
                viewModel.move(d.nodeId, targetId)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        is BrowserDialog.ConfirmTrash -> ConfirmDialog(
            title = "Mover para a lixeira?",
            text = "\"${d.name}\" ficará na lixeira durante 30 dias antes de ser eliminado. " +
                "Os ficheiros físicos não são apagados agora.",
            confirmLabel = "Mover para lixeira",
            onConfirm = {
                viewModel.trash(d.nodeId)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        is BrowserDialog.ConfirmRemoveRef -> ConfirmDialog(
            title = "Remover referência?",
            text = "Apenas a referência \"${d.name}\" será removida do workspace. " +
                "O ficheiro físico permanece intacto na sua localização original.",
            confirmLabel = "Remover referência",
            onConfirm = {
                viewModel.removeRef(d.nodeId)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        is BrowserDialog.Consolidate -> ConfirmDialog(
            title = "Consolidar fisicamente?",
            text = "Todos os ficheiros referenciados nesta pasta serão movidos para uma pasta física " +
                "à sua escolha. A operação corre em background com notificação de progresso.",
            confirmLabel = "Escolher destino",
            onConfirm = {
                consolidateTargetNode = d.nodeId
                dialog = null
                consolidatePicker.launch(null)
            },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }

    properties?.let { props ->
        PropertiesDialog(props = props, onDismiss = { viewModel.dismissProperties() })
    }
}

@Composable
private fun Breadcrumbs(path: List<String>, onCrumbClick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Home,
            contentDescription = "Raiz",
            modifier = Modifier.clickable { onCrumbClick(-1) }
        )
        path.forEachIndexed { index, name ->
            Text(" / ", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onCrumbClick(index) }
            )
        }
    }
}

@Composable
private fun NodeRow(
    item: NodeWithStorage,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRemoveRef: () -> Unit,
    onConsolidate: () -> Unit,
    onPin: () -> Unit,
    onProperties: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val node = item.node
    val isFolder = node.type != NodeType.FILE_REFERENCE
    val broken = item.storageObject?.permissionStatus == PermissionStatus.BROKEN

    ListItem(
        modifier = Modifier.clickable { onOpen() },
        leadingContent = {
            Icon(
                imageVector = if (isFolder) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = when {
                    broken -> MaterialTheme.colorScheme.error
                    isFolder -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        headlineContent = { Text(node.name) },
        supportingContent = {
            val backendLabel = when (item.storageObject?.backendType) {
                StorageBackend.APP_PRIVATE -> "Privado da app"
                StorageBackend.SAF_TREE -> "Pasta mapeada (SAF)"
                StorageBackend.MEDIASTORE -> "MediaStore"
                null -> if (isFolder) "Pasta virtual" else null
            }
            val text = listOfNotNull(
                backendLabel,
                item.storageObject?.let { formatSize(it.size) },
                if (broken) "Referência quebrada" else null
            ).joinToString(" · ")
            if (text.isNotEmpty()) Text(text)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Fixado")
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover para…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                        onClick = { menuOpen = false; onMove() }
                    )
                    if (isFolder) {
                        DropdownMenuItem(
                            text = { Text(if (node.isPinned) "Desafixar" else "Fixar na raiz") },
                            leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                            onClick = { menuOpen = false; onPin() }
                        )
                        DropdownMenuItem(
                            text = { Text("Consolidar fisicamente") },
                            onClick = { menuOpen = false; onConsolidate() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Remover referência") },
                            leadingIcon = { Icon(Icons.Filled.LinkOff, null) },
                            onClick = { menuOpen = false; onRemoveRef() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Propriedades") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = { menuOpen = false; onProperties() }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover para lixeira") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menuOpen = false; onTrash() }
                    )
                }
            }
        }
    )
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30).toDouble())
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20).toDouble())
    bytes >= 1 shl 10 -> "%.1f KB".format(bytes / (1 shl 10).toDouble())
    else -> "$bytes B"
}
