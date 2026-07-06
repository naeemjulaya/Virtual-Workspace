package com.virtualworkspace.infrastructure.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.virtualworkspace.R
import com.virtualworkspace.data.db.VirtualNodeEntity
import com.virtualworkspace.data.db.NodeWithStorageRow
import com.virtualworkspace.data.db.WorkspaceDatabase
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.StorageBackend
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Expõe a árvore virtual como Document Provider nativo (RF07, Fluxo 7).
 * Cada workspace é uma raiz; navegar devolve os nós virtuais; abrir um
 * FILE_REFERENCE delega no ficheiro físico subjacente.
 *
 * IDs de documento: "w<workspaceId>" (raiz) e "n<nodeId>" (nó).
 */
class WorkspaceDocumentsProvider : DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun database(): WorkspaceDatabase
    }

    private val db: WorkspaceDatabase by lazy {
        EntryPointAccessors.fromApplication(
            requireNotNull(context).applicationContext,
            ProviderEntryPoint::class.java
        ).database()
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
        )

        private fun workspaceDocId(workspaceId: Long) = "w$workspaceId"
        private fun nodeDocId(nodeId: Long) = "n$nodeId"
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val workspaces = runBlocking { db.workspaceRootDao().listAll() }
        for (workspace in workspaces) {
            cursor.newRow().apply {
                add(Root.COLUMN_ROOT_ID, "workspace_${workspace.id}")
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(Root.COLUMN_TITLE, workspace.name)
                add(Root.COLUMN_SUMMARY, workspace.description)
                add(Root.COLUMN_DOCUMENT_ID, workspaceDocId(workspace.id))
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (documentId.startsWith("w")) {
            val workspaceId = parseId(documentId, 'w')
            val workspace = runBlocking { db.workspaceRootDao().getById(workspaceId) }
                ?: throw FileNotFoundException("Workspace não encontrado: $documentId")
            cursor.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, documentId)
                add(Document.COLUMN_DISPLAY_NAME, workspace.name)
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_SIZE, 0L)
                add(Document.COLUMN_LAST_MODIFIED, workspace.updatedAt)
                add(Document.COLUMN_FLAGS, 0)
            }
        } else {
            val node = runBlocking { db.virtualNodeDao().getById(parseId(documentId, 'n')) }
                ?: throw FileNotFoundException("Nó não encontrado: $documentId")
            addNodeRow(cursor, node)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val children = runBlocking {
            if (parentDocumentId.startsWith("w")) {
                db.virtualNodeDao().getChildrenWithStorage(parseId(parentDocumentId, 'w'), null)
            } else {
                val node = db.virtualNodeDao().getById(parseId(parentDocumentId, 'n'))
                    ?: return@runBlocking emptyList()
                db.virtualNodeDao().getChildrenWithStorage(node.workspaceId, node.id)
            }
        }
        children.forEach { addNodeRow(cursor, it.node, it.storage) }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (!documentId.startsWith("n")) return false
        return runBlocking {
            val node = db.virtualNodeDao().getById(parseId(documentId, 'n')) ?: return@runBlocking false
            if (parentDocumentId.startsWith("w")) {
                node.workspaceId == parseId(parentDocumentId, 'w')
            } else {
                val parent = db.virtualNodeDao().getById(parseId(parentDocumentId, 'n'))
                    ?: return@runBlocking false
                node.workspaceId == parent.workspaceId &&
                    node.virtualPath.startsWith(parent.virtualPath + "/")
            }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (!documentId.startsWith("n")) throw FileNotFoundException("Não é um ficheiro: $documentId")
        if (mode != "r") throw FileNotFoundException("O workspace virtual é apenas de leitura")
        val (storageUri, backend) = runBlocking {
            val node = db.virtualNodeDao().getById(parseId(documentId, 'n'))
                ?: throw FileNotFoundException("Nó não encontrado")
            val soId = node.storageObjectId ?: throw FileNotFoundException("Nó sem ficheiro associado")
            val storage = db.storageObjectDao().getById(soId)
                ?: throw FileNotFoundException("StorageObject não encontrado")
            storage.uri to StorageBackend.valueOf(storage.backendType)
        }

        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return when (backend) {
            StorageBackend.APP_PRIVATE -> {
                val path = Uri.parse(storageUri).path ?: throw FileNotFoundException(storageUri)
                ParcelFileDescriptor.open(File(path), accessMode)
            }
            else -> requireNotNull(context).contentResolver
                .openFileDescriptor(Uri.parse(storageUri), mode)
                ?: throw FileNotFoundException("Falha ao abrir: $storageUri")
        }
    }

    private fun addNodeRow(cursor: MatrixCursor, node: VirtualNodeEntity, knownStorage: com.virtualworkspace.data.db.StorageObjectEntity? = null) {
        val isFolder = node.type != NodeType.FILE_REFERENCE.name
        val storage = knownStorage ?: if (isFolder) null else runBlocking {
            node.storageObjectId?.let { db.storageObjectDao().getById(it) }
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, nodeDocId(node.id))
            add(Document.COLUMN_DISPLAY_NAME, node.name)
            add(
                Document.COLUMN_MIME_TYPE,
                if (isFolder) Document.MIME_TYPE_DIR else storage?.mimeType ?: "application/octet-stream"
            )
            add(Document.COLUMN_SIZE, storage?.size ?: 0L)
            add(Document.COLUMN_LAST_MODIFIED, node.updatedAt)
            add(Document.COLUMN_FLAGS, 0)
        }
    }

    private fun parseId(documentId: String, prefix: Char): Long {
        if (documentId.firstOrNull() != prefix) throw FileNotFoundException("ID inválido: $documentId")
        return documentId.drop(1).toLongOrNull()
            ?: throw FileNotFoundException("ID inválido: $documentId")
    }
}
