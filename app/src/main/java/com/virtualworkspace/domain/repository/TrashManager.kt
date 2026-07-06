package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashManager {
    fun observeTrash(): Flow<List<NodeWithStorage>>

    /** Soft-delete: marca o nó (e descendentes) como apagados e cria TrashItems. */
    suspend fun moveToTrash(nodeId: Long)

    suspend fun restore(nodeId: Long)

    /** Apaga permanentemente (metadados + ficheiro físico se APP_PRIVATE). */
    suspend fun deletePermanently(nodeId: Long)

    /** Limpa itens expirados; devolve o número de itens removidos. */
    suspend fun cleanExpired(): Int

    suspend fun getTrashItemForNode(nodeId: Long): TrashItem?
}
