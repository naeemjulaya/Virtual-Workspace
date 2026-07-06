package com.virtualworkspace.domain.usecase

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.virtualworkspace.domain.model.OperationType
import com.virtualworkspace.domain.repository.OperationJournal
import com.virtualworkspace.domain.repository.WorkspaceRepository
import com.virtualworkspace.infrastructure.worker.ConsolidationWorker
import javax.inject.Inject

/**
 * Consolidação física opcional (RF04): move todos os ficheiros referenciados
 * numa pasta virtual para uma árvore SAF real, em background via WorkManager,
 * com journal para retomada/compensação (RNF02, RNF03).
 */
class ConsolidateFolderUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val operationJournal: OperationJournal,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(folderNodeId: Long, targetTreeUri: String): Result<Long> {
        val refs = workspaceRepository.getFileReferencesInSubtree(folderNodeId)
        val folder = workspaceRepository.getNode(folderNodeId)
        if (folder == null || folder.type == com.virtualworkspace.domain.model.NodeType.FILE_REFERENCE) {
            return Result.failure(IllegalArgumentException("Selecione uma pasta válida"))
        }
        if (refs.isEmpty()) {
            return Result.failure(IllegalStateException("A pasta não contém referências a ficheiros"))
        }

        val journalId = operationJournal.begin(
            type = OperationType.CONSOLIDATE,
            totalItems = refs.size,
            sourceNodeId = folderNodeId
        )

        val request = OneTimeWorkRequestBuilder<ConsolidationWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(ConsolidationWorker.KEY_JOURNAL_ID, journalId)
                    .putLong(ConsolidationWorker.KEY_FOLDER_NODE_ID, folderNodeId)
                    .putString(ConsolidationWorker.KEY_TARGET_TREE_URI, targetTreeUri)
                    .build()
            )
            .build()
        workManager.enqueue(request)
        return Result.success(journalId)
    }
}
