package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.repository.TrashManager
import javax.inject.Inject

/** Soft-delete: move para a lixeira com retenção de 30 dias (RF06). */
class MoveToTrashUseCase @Inject constructor(
    private val trashManager: TrashManager
) {
    suspend operator fun invoke(nodeId: Long): Result<Unit> =
        runCatching { trashManager.moveToTrash(nodeId) }
}

class RestoreFromTrashUseCase @Inject constructor(
    private val trashManager: TrashManager
) {
    suspend operator fun invoke(nodeId: Long): Result<Unit> =
        runCatching { trashManager.restore(nodeId) }
}

class PermanentlyDeleteUseCase @Inject constructor(
    private val trashManager: TrashManager
) {
    suspend operator fun invoke(nodeId: Long): Result<Unit> =
        runCatching { trashManager.deletePermanently(nodeId) }
}

class CleanExpiredTrashUseCase @Inject constructor(
    private val trashManager: TrashManager
) {
    suspend operator fun invoke(): Int = trashManager.cleanExpired()
}
