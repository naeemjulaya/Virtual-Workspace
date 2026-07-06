package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.StorageBackend

data class PhysicalFileMetadata(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val lastModifiedEpochMs: Long
)

/**
 * Abstração sobre os backends físicos (SAF, app-private, MediaStore).
 * O domínio nunca toca diretamente em ContentResolver/File.
 */
interface PhysicalStorageGateway {

    /** Metadados do ficheiro, ou null se o URI já não for acessível (referência quebrada). */
    suspend fun queryMetadata(uri: String, backend: StorageBackend): PhysicalFileMetadata?

    suspend fun exists(uri: String, backend: StorageBackend): Boolean

    /**
     * Copia o ficheiro para uma árvore SAF de destino. Devolve o URI do novo documento.
     * Usado na consolidação física e exportação.
     */
    suspend fun copyToSafTree(sourceUri: String, sourceBackend: StorageBackend, targetTreeUri: String, displayName: String, mimeType: String): String

    /** Copia o conteúdo do URI para o armazenamento privado da app. Devolve o URI (file://). */
    suspend fun copyToAppPrivate(sourceUri: String, displayName: String): String

    /** Apaga o ficheiro físico. Só permitido para APP_PRIVATE e documentos SAF com permissão de escrita. */
    suspend fun deletePhysical(uri: String, backend: StorageBackend): Boolean

    /** SHA-256 do conteúdo, ou null se inacessível. */
    suspend fun computeChecksum(uri: String, backend: StorageBackend): String?

    /** Lista ficheiros (não recursivo) de uma árvore SAF mapeada. */
    suspend fun listSafTreeChildren(treeUri: String): List<PhysicalFileMetadata>
}
