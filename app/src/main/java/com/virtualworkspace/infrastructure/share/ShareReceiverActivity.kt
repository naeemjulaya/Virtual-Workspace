package com.virtualworkspace.infrastructure.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.data.storage.ImportManager
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.model.WorkspaceRoot
import com.virtualworkspace.domain.repository.WorkspaceRepository
import com.virtualworkspace.presentation.common.FolderPickerDialog
import com.virtualworkspace.presentation.theme.VirtualWorkspaceTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val importManager: ImportManager,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    sealed interface State {
        data object Importing : State
        data class ChooseDestination(val imported: List<ImportManager.ImportedFile>) : State
        data class Done(val count: Int) : State
        data object Cancelled : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Importing)
    val state: StateFlow<State> = _state

    val workspaces: StateFlow<List<WorkspaceRoot>> = workspaceRepository
        .observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var importStarted = false

    /** Fluxo 6: copiar para app-private storage e criar StorageObjects. */
    fun import(uris: List<Uri>) {
        if (importStarted) return
        importStarted = true
        viewModelScope.launch {
            runCatching { importManager.importAll(uris) }
                .onSuccess { _state.value = State.ChooseDestination(it) }
                .onFailure { _state.value = State.Error(it.message ?: "Falha na importação") }
        }
    }

    fun cancel() {
        val imported = (state.value as? State.ChooseDestination)?.imported ?: return
        _state.value = State.Importing
        viewModelScope.launch {
            importManager.discard(imported)
            _state.value = State.Cancelled
        }
    }

    fun saveTo(workspaceId: Long, folderId: Long?) {
        val imported = (state.value as? State.ChooseDestination)?.imported ?: return
        viewModelScope.launch {
            val createdNodeIds = mutableListOf<Long>()
            runCatching {
                val destination = folderId?.let { workspaceRepository.getNode(it) }
                require(folderId == null || (destination != null && destination.workspaceId == workspaceId &&
                    destination.type == NodeType.FOLDER && !destination.isDeleted)) { "Pasta de destino inválida" }
                val parentPath = folderId?.let { workspaceRepository.getNode(it)?.virtualPath } ?: ""
                for (file in imported) {
                    var name = file.displayName
                    var counter = 1
                    while (workspaceRepository.childExists(workspaceId, folderId, name)) {
                        val base = file.displayName.substringBeforeLast('.', file.displayName)
                        val ext = file.displayName.substringAfterLast('.', "")
                        name = if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext"
                        counter++
                    }
                    createdNodeIds += workspaceRepository.createNode(
                        workspaceId = workspaceId,
                        parentId = folderId,
                        name = name,
                        type = NodeType.FILE_REFERENCE,
                        virtualPath = "$parentPath/$name",
                        storageObjectId = file.storageObject.id
                    )
                }
            }
                .onSuccess { _state.value = State.Done(imported.size) }
                .onFailure {
                    createdNodeIds.forEach { id -> runCatching { workspaceRepository.deleteNodePermanently(id) } }
                    importManager.discard(imported)
                    _state.value = State.Error(it.message ?: "Falha ao guardar")
                }
        }
    }

    suspend fun getFolders(workspaceId: Long, parentId: Long?): List<VirtualNode> =
        workspaceRepository.getChildren(workspaceId, parentId)
            .filter { it.type == NodeType.FOLDER }
}

/** Share Target (RF08): recebe ACTION_SEND / ACTION_SEND_MULTIPLE de outras apps. */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "Nada para importar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.import(uris)

        setContent {
            VirtualWorkspaceTheme {
                ShareImportFlow(
                    viewModel = viewModel,
                    onFinished = { message ->
                        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(getStreamExtra(intent))
        Intent.ACTION_SEND_MULTIPLE -> getStreamListExtra(intent)
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun getStreamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun getStreamListExtra(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
        }
}

@Composable
private fun ShareImportFlow(
    viewModel: ShareImportViewModel,
    onFinished: (message: String?) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()

    when (val s = state) {
        is ShareImportViewModel.State.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("A importar…") },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )

        is ShareImportViewModel.State.ChooseDestination -> {
            // Passo 1: escolher workspace; passo 2: escolher pasta
            var selectedWorkspace by remember { mutableStateOf<WorkspaceRoot?>(null) }
            val workspace = selectedWorkspace
            if (workspace == null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancel() },
                    title = { Text("Guardar ${s.imported.size} ficheiro(s) em…") },
                    text = {
                        if (workspaces.isEmpty()) {
                            Text("Crie primeiro um workspace na app.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(workspaces, key = { it.id }) { ws ->
                                    ListItem(
                                        headlineContent = { Text(ws.name) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedWorkspace = ws }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancel() }) { Text("Cancelar") }
                    }
                )
            } else {
                FolderPickerDialog(
                    title = workspace.name,
                    loadFolders = { parentId -> viewModel.getFolders(workspace.id, parentId) },
                    onSelect = { folderId -> viewModel.saveTo(workspace.id, folderId) },
                    onDismiss = { selectedWorkspace = null }
                )
            }
        }

        is ShareImportViewModel.State.Done ->
            onFinished("${s.count} ficheiro(s) importado(s)")

        is ShareImportViewModel.State.Cancelled -> onFinished(null)

        is ShareImportViewModel.State.Error ->
            onFinished("Erro: ${s.message}")
    }
}
