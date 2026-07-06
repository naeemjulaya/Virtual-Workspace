package com.virtualworkspace.presentation.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.usecase.GetNodePropertiesUseCase
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

/** Propriedades detalhadas de um nó (RF14). */
@Composable
fun PropertiesDialog(
    props: GetNodePropertiesUseCase.NodeProperties,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(props.node.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PropertyRow("Tipo", when (props.node.type) {
                    NodeType.FOLDER -> "Pasta virtual"
                    NodeType.FILE_REFERENCE -> "Referência a ficheiro"
                    NodeType.SMART_FOLDER -> "Smart folder"
                })
                PropertyRow("Caminho virtual", props.node.virtualPath)
                PropertyRow("Criado em", dateFormatter.format(props.node.createdAt))
                PropertyRow("Atualizado em", dateFormatter.format(props.node.updatedAt))

                props.storageObject?.let { so ->
                    PropertyRow("Localização física", so.uri)
                    PropertyRow("Backend", so.backendType.name)
                    PropertyRow("Tipo MIME", so.mimeType)
                    PropertyRow("Tamanho", formatSize(so.size))
                    PropertyRow("Modificado em", dateFormatter.format(so.lastModified))
                    PropertyRow("Estado da permissão", so.permissionStatus.name)
                    so.checksum?.let { PropertyRow("Checksum (SHA-256)", it) }
                }

                if (props.referencedIn.size > 1) {
                    PropertyRow(
                        "Referenciado em ${props.referencedIn.size} pastas",
                        props.referencedIn.joinToString("\n")
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
