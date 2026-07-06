package com.virtualworkspace.presentation.workspaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.domain.model.WorkspaceRoot
import com.virtualworkspace.domain.repository.WorkspaceRepository
import com.virtualworkspace.presentation.common.ConfirmDialog
import com.virtualworkspace.presentation.common.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkspaceListViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    val workspaces: StateFlow<List<WorkspaceRoot>> = workspaceRepository
        .observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { workspaceRepository.createWorkspace(name, null) }
                .onFailure { _message.value = it.message ?: "Não foi possível criar o workspace" }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { workspaceRepository.deleteWorkspace(id) }
                .onFailure { _message.value = it.message ?: "Não foi possível apagar o workspace" }
        }
    }
    fun consumeMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceListScreen(
    onOpenWorkspace: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: WorkspaceListViewModel = hiltViewModel()
) {
    val workspaces by viewModel.workspaces.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WorkspaceRoot?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Workspaces") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Definições")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Novo workspace")
            }
        }
    ) { padding ->
        if (workspaces.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Workspaces,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Sem workspaces", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Crie o primeiro workspace (ex: Trabalho, Universidade, Pessoal) com o botão +",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(workspaces, key = { it.id }) { workspace ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenWorkspace(workspace.id) },
                        leadingContent = { Icon(Icons.Filled.Workspaces, contentDescription = null) },
                        headlineContent = { Text(workspace.name) },
                        supportingContent = workspace.description?.let { { Text(it) } },
                        trailingContent = {
                            IconButton(onClick = { confirmDelete = workspace }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Apagar")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "Novo workspace",
            label = "Nome (ex: Trabalho)",
            confirmLabel = "Criar",
            onConfirm = { name ->
                viewModel.create(name)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    confirmDelete?.let { workspace ->
        ConfirmDialog(
            title = "Apagar workspace?",
            text = "\"${workspace.name}\" e toda a sua árvore virtual serão removidos. " +
                "Os ficheiros físicos referenciados não são apagados.",
            confirmLabel = "Apagar",
            onConfirm = {
                viewModel.delete(workspace.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null }
        )
    }
}
