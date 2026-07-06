package com.virtualworkspace.data.storage

import android.content.Context
import android.net.Uri
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.StorageObject
import com.virtualworkspace.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recebe URIs de Intents de partilha (ACTION_SEND / ACTION_SEND_MULTIPLE),
 * copia o conteúdo para o armazenamento privado da app e cria o StorageObject.
 * A criação do VirtualNode é feita depois, quando o utilizador escolhe a pasta destino.
 */
@Singleton
class ImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val physicalStorage: PhysicalStorageGatewayImpl,
    private val storageRepository: StorageRepository,
    private val clock: Clock
) {

    data class ImportedFile(val storageObject: StorageObject, val displayName: String)

    suspend fun importFromUri(sourceUri: Uri): ImportedFile = withContext(Dispatchers.IO) {
        val displayName = physicalStorage.queryDisplayName(sourceUri)
            ?: sourceUri.lastPathSegment?.substringAfterLast('/')
            ?: "importado_${clock.millis()}"
        val mimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"

        val privateUri = physicalStorage.copyToAppPrivate(sourceUri.toString(), displayName)
        val metadata = physicalStorage.queryMetadata(privateUri, StorageBackend.APP_PRIVATE)
            ?: error("Ficheiro importado desapareceu: $privateUri")

        val storageObject = storageRepository.getOrCreate(
            uri = privateUri,
            backendType = StorageBackend.APP_PRIVATE,
            mimeType = mimeType,
            size = metadata.size,
            lastModifiedEpochMs = metadata.lastModifiedEpochMs
        )
        ImportedFile(storageObject, metadata.displayName)
    }

    suspend fun importAll(uris: List<Uri>): List<ImportedFile> {
        val imported = mutableListOf<ImportedFile>()
        try {
            uris.forEach { imported += importFromUri(it) }
            return imported
        } catch (error: Exception) {
            discard(imported)
            throw error
        }
    }

    suspend fun discard(files: List<ImportedFile>) = withContext(Dispatchers.IO) {
        files.forEach { file ->
            val storage = file.storageObject
            if (physicalStorage.deletePhysical(storage.uri, StorageBackend.APP_PRIVATE)) {
                storageRepository.delete(storage.id)
            }
        }
    }
}
