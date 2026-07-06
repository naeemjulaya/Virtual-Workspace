package com.virtualworkspace.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualworkspace.domain.model.PermissionGrant
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.repository.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    val grants: StateFlow<List<PermissionGrant>> = permissionManager
        .observeGrants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun revoke(uri: String) {
        viewModelScope.launch { permissionManager.revokeGrant(uri) }
    }

    fun validateNow() {
        viewModelScope.launch { permissionManager.validateAll() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val grants by viewModel.grants.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Definições") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.validateNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Validar permissões agora")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Lixeira") },
                supportingContent = { Text("Itens apagados, retenção de 30 dias") },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                modifier = Modifier.padding(0.dp),
                trailingContent = {
                    androidx.compose.material3.TextButton(onClick = onOpenTrash) { Text("Abrir") }
                }
            )
            HorizontalDivider()
            Text(
                "Permissões SAF persistentes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (grants.isEmpty()) {
                Text(
                    "Nenhuma pasta física mapeada. Use \"Mapear pasta física\" no browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyColumn {
                    items(grants, key = { it.id }) { grant ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Filled.FolderShared,
                                    contentDescription = null,
                                    tint = if (grant.status == PermissionStatus.VALID) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            },
                            headlineContent = {
                                Text(grant.uri.substringAfterLast("%3A").ifEmpty { grant.uri }, maxLines = 1)
                            },
                            supportingContent = {
                                Text(
                                    when (grant.status) {
                                        PermissionStatus.VALID -> "Válida"
                                        PermissionStatus.EXPIRED -> "Expirada — renove mapeando novamente"
                                        PermissionStatus.REVOKED -> "Revogada"
                                        PermissionStatus.BROKEN -> "Quebrada"
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.revoke(grant.uri) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Revogar")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
