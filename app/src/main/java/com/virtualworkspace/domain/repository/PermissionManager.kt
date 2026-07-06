package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.PermissionGrant
import kotlinx.coroutines.flow.Flow

interface PermissionManager {
    fun observeGrants(): Flow<List<PermissionGrant>>
    suspend fun recordGrant(uri: String, flags: Int)
    suspend fun revokeGrant(uri: String)

    /** Verifica se o URI ainda tem permissão persistente válida. */
    suspend fun isUriAccessible(uri: String): Boolean

    /** Valida todas as permissões ativas; devolve as que expiraram. */
    suspend fun validateAll(): List<PermissionGrant>
}
