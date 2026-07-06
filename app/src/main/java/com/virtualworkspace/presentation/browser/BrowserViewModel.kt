package com.virtualworkspace.presentation.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.repository.WorkspaceRepository
import com.virtualworkspace.domain.usecase.AddReferenceUseCase
import com.virtualworkspace.domain.usecase.ConsolidateFolderUseCase
import com.virtualworkspace.domain.usecase.CreateFolderUseCase
import com.virtualworkspace.domain.usecase.GetNodePropertiesUseCase
import com.virtualworkspace.domain.usecase.MapSafFolderUseCase
import com.virtualworkspace.domain.usecase.MoveNodeUseCase
import com.virtualworkspace.domain.usecase.MoveToTrashUseCase
import com.virtualworkspace.domain.usecase.RemoveReferenceUseCase
import com.virtualworkspace.domain.usecase.RenameNodeUseCase
import com.virtualworkspace.domain.usecase.TogglePinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRepository: WorkspaceRepository,
    private val createFolderUseCase: CreateFolderUseCase,
    private val renameNodeUseCase: RenameNodeUseCase,
    private val moveNodeUseCase: MoveNodeUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val addReferenceUseCase: AddReferenceUseCase,
    private val removeReferenceUseCase: RemoveReferenceUseCase,
    private val mapSafFolderUseCase: MapSafFolderUseCase,
    private val consolidateFolderUseCase: ConsolidateFolderUseCase,
    private val togglePinUseCase: TogglePinUseCase,
    private val getNodePropertiesUseCase: GetNodePropertiesUseCase
) : ViewModel() {

    val workspaceId: Long = checkNotNull(savedStateHandle["workspaceId"])

    /** Pilha de pastas abertas (vazia = raiz do workspace). Serve de breadcrumbs. */
    private val _breadcrumbs = MutableStateFlow<List<VirtualNode>>(emptyList())
    val breadcrumbs: StateFlow<List<VirtualNode>> = _breadcrumbs

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _properties = MutableStateFlow<GetNodePropertiesUseCase.NodeProperties?>(null)
    val properties: StateFlow<GetNodePropertiesUseCase.NodeProperties?> = _properties

    val currentFolderId: Long? get() = _breadcrumbs.value.lastOrNull()?.id

    init {
        savedStateHandle.get<Long>("folderId")?.takeIf { it >= 0 }?.let { folderId ->
            viewModelScope.launch {
                val stack = mutableListOf<VirtualNode>()
                var current = workspaceRepository.getNode(folderId)
                while (current != null && current.workspaceId == workspaceId) {
                    stack.add(0, current)
                    current = current.parentId?.let { workspaceRepository.getNode(it) }
                }
                _breadcrumbs.value = stack
            }
        }
    }

    val children: StateFlow<List<NodeWithStorage>> = _breadcrumbs
        .flatMapLatest { stack ->
            workspaceRepository.observeChildren(workspaceId, stack.lastOrNull()?.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinned: StateFlow<List<VirtualNode>> = workspaceRepository
        .observePinned(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openFolder(node: VirtualNode) {
        if (node.type == NodeType.FILE_REFERENCE) return
        _breadcrumbs.value = _breadcrumbs.value + node
    }

    fun navigateToBreadcrumb(index: Int) {
        // index -1 = raiz
        _breadcrumbs.value = if (index < 0) emptyList() else _breadcrumbs.value.take(index + 1)
    }

    fun goUp(): Boolean {
        if (_breadcrumbs.value.isEmpty()) return false
        _breadcrumbs.value = _breadcrumbs.value.dropLast(1)
        return true
    }

    fun openPinned(node: VirtualNode) {
        viewModelScope.launch {
            // Reconstruir a pilha de breadcrumbs a partir do caminho do nó
            val stack = mutableListOf<VirtualNode>()
            var current: VirtualNode? = node
            while (current != null) {
                stack.add(0, current)
                current = current.parentId?.let { workspaceRepository.getNode(it) }
            }
            _breadcrumbs.value = stack
        }
    }

    fun createFolder(name: String) = launchWithFeedback {
        createFolderUseCase(workspaceId, currentFolderId, name)
    }

    fun rename(nodeId: Long, newName: String) = launchWithFeedback {
        renameNodeUseCase(nodeId, newName)
    }

    fun move(nodeId: Long, targetFolderId: Long?) = launchWithFeedback {
        moveNodeUseCase(nodeId, targetFolderId)
    }

    fun trash(nodeId: Long) = launchWithFeedback(successMessage = "Movido para a lixeira") {
        moveToTrashUseCase(nodeId)
    }

    fun removeRef(nodeId: Long) =
        launchWithFeedback(successMessage = "Referência removida (ficheiro físico intacto)") {
            removeReferenceUseCase(nodeId)
        }

    /** URI vinda do picker ACTION_OPEN_DOCUMENT. */
    fun addFileReference(uri: String) = launchWithFeedback {
        addReferenceUseCase(workspaceId, currentFolderId, uri, StorageBackend.SAF_TREE)
    }

    /** URI de árvore vinda do picker ACTION_OPEN_DOCUMENT_TREE (permissão já persistida na UI). */
    fun mapFolder(treeUri: String, grantFlags: Int, displayName: String) =
        launchWithFeedback(successMessage = "Pasta mapeada") {
            mapSafFolderUseCase(workspaceId, currentFolderId, treeUri, grantFlags, displayName)
        }

    fun consolidate(folderNodeId: Long, targetTreeUri: String) =
        launchWithFeedback(successMessage = "Consolidação iniciada em background") {
            consolidateFolderUseCase(folderNodeId, targetTreeUri)
        }

    fun setPinned(nodeId: Long, pinned: Boolean) {
        viewModelScope.launch { togglePinUseCase(nodeId, pinned) }
    }

    fun showProperties(nodeId: Long) {
        viewModelScope.launch { _properties.value = getNodePropertiesUseCase(nodeId) }
    }

    fun dismissProperties() {
        _properties.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun reportError(message: String) {
        _message.value = message
    }

    /** Pastas para o diálogo "Mover para…". */
    suspend fun getFolders(parentId: Long?): List<VirtualNode> =
        workspaceRepository.getChildren(workspaceId, parentId)
            .filter { it.type == NodeType.FOLDER }

    private fun launchWithFeedback(successMessage: String? = null, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block()
                .onSuccess { if (successMessage != null) _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Operação falhou" }
        }
    }
}
