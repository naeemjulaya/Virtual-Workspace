package com.virtualworkspace.presentation.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.usecase.SearchFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchFiles: SearchFilesUseCase
) : ViewModel() {

    private val workspaceId: Long? = savedStateHandle.get<Long>("workspaceId")?.takeIf { it >= 0 }

    val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<NodeWithStorage>>(emptyList())
    val results: StateFlow<List<NodeWithStorage>> = _results

    init {
        viewModelScope.launch {
            query.debounce(250).collect { q ->
                _results.value = searchFiles(workspaceId, q)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onOpenNode: (NodeWithStorage) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pesquisar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                label = { Text("Nome do ficheiro ou pasta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(results, key = { it.node.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenNode(item) },
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
                        supportingContent = {
                            Column {
                                Text("Virtual: ${item.node.virtualPath}")
                                item.storageObject?.let { Text("Físico: ${it.uri}", maxLines = 1) }
                            }
                        }
                    )
                }
            }
        }
    }
}
