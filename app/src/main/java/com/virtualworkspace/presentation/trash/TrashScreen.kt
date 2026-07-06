package com.virtualworkspace.presentation.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.repository.TrashManager
import com.virtualworkspace.domain.usecase.PermanentlyDeleteUseCase
import com.virtualworkspace.domain.usecase.RestoreFromTrashUseCase
import com.virtualworkspace.presentation.common.ConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    trashManager: TrashManager,
    private val restoreFromTrash: RestoreFromTrashUseCase,
    private val permanentlyDelete: PermanentlyDeleteUseCase
) : ViewModel() {

    val items: StateFlow<List<NodeWithStorage>> = trashManager
        .observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(nodeId: Long) {
        viewModelScope.launch { restoreFromTrash(nodeId) }
    }

    fun deleteForever(nodeId: Long) {
        viewModelScope.launch { permanentlyDelete(nodeId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    var confirmDelete by remember { mutableStateOf<NodeWithStorage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lixeira") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Lixeira vazia", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Itens apagados ficam aqui 30 dias antes da eliminação permanente.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.node.id }) { item ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = if (item.node.type == NodeType.FILE_REFERENCE) {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                } else {
                                    Icons.Filled.Folder
                                },
                                contentDescription = null
                            )
                        },
                        headlineContent = { Text(item.node.name) },
                        supportingContent = { Text(item.node.virtualPath) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.restore(item.node.id) }) {
                                    Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restaurar")
                                }
                                IconButton(onClick = { confirmDelete = item }) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = "Apagar permanentemente",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Apagar permanentemente?",
            text = "\"${item.node.name}\" será eliminado definitivamente. " +
                "Ficheiros importados para a app também serão apagados. Esta ação é irreversível.",
            confirmLabel = "Apagar para sempre",
            onConfirm = {
                viewModel.deleteForever(item.node.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null }
        )
    }
}
