package com.virtualworkspace.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.repository.OperationJournal
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.WorkspaceRepository
import com.virtualworkspace.infrastructure.notification.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Consolidação física (Fluxo 5): copia cada ficheiro referenciado para a árvore
 * SAF de destino, atualiza o URI do StorageObject e regista progresso no journal.
 * Em caso de falha, compensa apagando os ficheiros já copiados (RNF03).
 */
@HiltWorker
class ConsolidationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workspaceRepository: WorkspaceRepository,
    private val storageRepository: StorageRepository,
    private val physicalStorage: PhysicalStorageGateway,
    private val journal: OperationJournal,
    private val notifier: AppNotifier
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_JOURNAL_ID = "journal_id"
        const val KEY_FOLDER_NODE_ID = "folder_node_id"
        const val KEY_TARGET_TREE_URI = "target_tree_uri"
    }

    override suspend fun doWork(): Result {
        val journalId = inputData.getLong(KEY_JOURNAL_ID, -1)
        val folderNodeId = inputData.getLong(KEY_FOLDER_NODE_ID, -1)
        val targetTreeUri = inputData.getString(KEY_TARGET_TREE_URI)
        if (journalId < 0 || folderNodeId < 0 || targetTreeUri == null) return Result.failure()

        setForeground(notifier.foregroundInfo("A consolidar ficheiros…"))
        // Recuperação após morte do processo: se alguma cópia já está referenciada,
        // o commit terminou e basta concluir o journal. Caso contrário eram cópias
        // temporárias e podem ser removidas antes de recomeçar.
        val previousUris = journal.get(journalId)?.compensationData?.let { encoded ->
            runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), encoded)
            }.getOrDefault(emptyList())
        }.orEmpty()
        if (previousUris.isNotEmpty()) {
            val committedPreviously = previousUris.any { uri ->
                storageRepository.getByUri(uri)?.let { storage ->
                    workspaceRepository.getNodesReferencing(storage.id).isNotEmpty()
                } == true
            }
            if (committedPreviously) {
                journal.complete(journalId)
                return Result.success()
            }
            previousUris.forEach { uri ->
                physicalStorage.deletePhysical(uri, StorageBackend.SAF_TREE)
                storageRepository.getByUri(uri)?.let { storageRepository.delete(it.id) }
            }
            journal.resetProgress(journalId)
        }
        journal.markRunning(journalId)
        val refs = workspaceRepository.getFileReferencesInSubtree(folderNodeId)
        val copiedUris = mutableListOf<String>()
        val createdStorageIds = mutableListOf<Long>()
        var committed = false

        try {
            // Um StorageObject partilhado é copiado uma só vez, mas apenas as referências
            // desta subárvore são reatribuídas ao novo objeto.
            val groupedRefs = refs.mapNotNull { ref ->
                ref.storageObjectId?.let { it to ref }
            }.groupBy({ it.first }, { it.second })
            val assignments = mutableMapOf<Long, Long>()

            groupedRefs.entries.forEachIndexed { index, (oldStorageId, nodes) ->
                val storageObject = storageRepository.getById(oldStorageId)
                    ?: error("Ficheiro associado já não existe")
                val representative = nodes.first()
                val newUri = physicalStorage.copyToSafTree(
                    sourceUri = storageObject.uri,
                    sourceBackend = storageObject.backendType,
                    targetTreeUri = targetTreeUri,
                    displayName = representative.name,
                    mimeType = storageObject.mimeType
                )
                copiedUris += newUri
                val metadata = physicalStorage.queryMetadata(newUri, StorageBackend.SAF_TREE)
                    ?: error("Não foi possível validar a cópia criada")
                val newStorage = storageRepository.getOrCreate(
                    uri = newUri,
                    backendType = StorageBackend.SAF_TREE,
                    mimeType = metadata.mimeType,
                    size = metadata.size,
                    lastModifiedEpochMs = metadata.lastModifiedEpochMs
                )
                createdStorageIds += newStorage.id
                nodes.forEach { assignments[it.id] = newStorage.id }

                journal.incrementProgress(
                    journalId,
                    Json.encodeToString(ListSerializer(String.serializer()), copiedUris)
                )
                notifier.notifyProgress("A consolidar ficheiros…", index + 1, groupedRefs.size)
            }

            // Commit lógico atómico: nenhuma referência aponta para as cópias até todas existirem.
            workspaceRepository.reassignStorageObjects(assignments)
            committed = true

            // Só depois do commit removemos origens privadas que ficaram realmente órfãs.
            groupedRefs.keys.forEach { oldStorageId ->
                runCatching {
                    if (workspaceRepository.getNodesReferencing(oldStorageId).isEmpty()) {
                        storageRepository.getById(oldStorageId)?.let { old ->
                            if (old.backendType != StorageBackend.APP_PRIVATE ||
                                physicalStorage.deletePhysical(old.uri, StorageBackend.APP_PRIVATE)) {
                                storageRepository.delete(oldStorageId)
                            }
                        }
                    }
                }
            }
            journal.complete(journalId)
            notifier.notifyDone(
                AppNotifier.NOTIFICATION_CONSOLIDATION,
                "Consolidação concluída",
                "${refs.size} referência(s) consolidadas na pasta de destino"
            )
            return Result.success()
        } catch (e: Exception) {
            if (committed) {
                journal.fail(journalId, "Consolidação aplicada, mas a finalização falhou: ${e.message}")
                return Result.failure()
            }
            // Compensação: apagar cópias já feitas no destino
            copiedUris.forEach { uri ->
                runCatching { physicalStorage.deletePhysical(uri, StorageBackend.SAF_TREE) }
            }
            createdStorageIds.forEach { id -> runCatching { storageRepository.delete(id) } }
            journal.fail(journalId, e.message ?: "Erro desconhecido")
            journal.markCompensated(journalId)
            notifier.notifyAlert(
                AppNotifier.NOTIFICATION_CONSOLIDATION,
                "Consolidação falhou",
                e.message ?: "Erro desconhecido — alterações revertidas"
            )
            return Result.failure()
        }
    }
}
