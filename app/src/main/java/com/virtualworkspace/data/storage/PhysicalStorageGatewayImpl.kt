package com.virtualworkspace.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.repository.PhysicalFileMetadata
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalStorageGatewayImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PhysicalStorageGateway {

    private val resolver get() = context.contentResolver

    /** Diretório privado onde ficam os ficheiros importados via Share Target. */
    private val importsDir: File
        get() = File(context.filesDir, "imports").apply { mkdirs() }

    override suspend fun queryMetadata(uri: String, backend: StorageBackend): PhysicalFileMetadata? =
        withContext(Dispatchers.IO) {
            when (backend) {
                StorageBackend.APP_PRIVATE -> {
                    val file = fileFromUri(uri) ?: return@withContext null
                    if (!file.exists()) return@withContext null
                    PhysicalFileMetadata(
                        uri = uri,
                        displayName = file.name,
                        mimeType = guessMime(file.name),
                        size = file.length(),
                        lastModifiedEpochMs = file.lastModified()
                    )
                }
                StorageBackend.SAF_TREE, StorageBackend.MEDIASTORE -> runCatching {
                    val parsed = Uri.parse(uri)
                    val doc = DocumentFile.fromSingleUri(context, parsed) ?: return@runCatching null
                    if (!doc.exists()) return@runCatching null
                    PhysicalFileMetadata(
                        uri = uri,
                        displayName = doc.name ?: "unknown",
                        mimeType = doc.type ?: "application/octet-stream",
                        size = doc.length(),
                        lastModifiedEpochMs = doc.lastModified()
                    )
                }.getOrNull()
            }
        }

    override suspend fun exists(uri: String, backend: StorageBackend): Boolean =
        queryMetadata(uri, backend) != null

    override suspend fun copyToSafTree(
        sourceUri: String,
        sourceBackend: StorageBackend,
        targetTreeUri: String,
        displayName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(targetTreeUri))
            ?: throw IOException("Árvore SAF de destino inacessível: $targetTreeUri")
        val target = tree.createFile(mimeType, displayName)
            ?: throw IOException("Falha ao criar documento '$displayName' no destino")

        try {
            openInput(sourceUri, sourceBackend).use { input ->
                resolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IOException("Falha ao abrir stream de escrita")
            }
        } catch (e: Exception) {
            runCatching { target.delete() }
            throw e
        }
        target.uri.toString()
    }

    override suspend fun copyToAppPrivate(sourceUri: String, displayName: String): String =
        withContext(Dispatchers.IO) {
            val safeName = displayName.replace(Regex("""[/\\:*?"<>|]"""), "_")
            var target = File(importsDir, safeName)
            var counter = 1
            while (target.exists()) {
                val base = safeName.substringBeforeLast('.', safeName)
                val ext = safeName.substringAfterLast('.', "")
                val suffixed = if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext"
                target = File(importsDir, suffixed)
                counter++
            }
            resolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Falha ao ler URI de origem: $sourceUri")
            Uri.fromFile(target).toString()
        }

    override suspend fun deletePhysical(uri: String, backend: StorageBackend): Boolean =
        withContext(Dispatchers.IO) {
            when (backend) {
                StorageBackend.APP_PRIVATE -> fileFromUri(uri)?.delete() == true
                StorageBackend.SAF_TREE -> runCatching {
                    DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
                }.getOrDefault(false)
                StorageBackend.MEDIASTORE -> runCatching {
                    resolver.delete(Uri.parse(uri), null, null) > 0
                }.getOrDefault(false)
            }
        }

    override suspend fun computeChecksum(uri: String, backend: StorageBackend): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                openInput(uri, backend).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        digest.update(buffer, 0, read)
                        read = input.read(buffer)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }

    override suspend fun listSafTreeChildren(treeUri: String): List<PhysicalFileMetadata> =
        withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext emptyList()
            tree.listFiles()
                .filter { it.isFile }
                .map { doc ->
                    PhysicalFileMetadata(
                        uri = doc.uri.toString(),
                        displayName = doc.name ?: "unknown",
                        mimeType = doc.type ?: "application/octet-stream",
                        size = doc.length(),
                        lastModifiedEpochMs = doc.lastModified()
                    )
                }
        }

    private fun openInput(uri: String, backend: StorageBackend) = when (backend) {
        StorageBackend.APP_PRIVATE -> {
            val file = fileFromUri(uri) ?: throw IOException("Ficheiro privado não encontrado: $uri")
            file.inputStream()
        }
        else -> resolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Falha ao abrir stream: $uri")
    }

    private fun fileFromUri(uri: String): File? {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "file") parsed.path?.let(::File) else null
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    /** Nome de exibição de um content URI (usado na importação). */
    fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
