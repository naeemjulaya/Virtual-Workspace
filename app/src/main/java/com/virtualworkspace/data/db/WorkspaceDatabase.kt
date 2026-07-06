package com.virtualworkspace.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceRootEntity::class,
        VirtualNodeEntity::class,
        StorageObjectEntity::class,
        OperationJournalEntity::class,
        PermissionGrantEntity::class,
        TrashItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class WorkspaceDatabase : RoomDatabase() {
    abstract fun workspaceRootDao(): WorkspaceRootDao
    abstract fun virtualNodeDao(): VirtualNodeDao
    abstract fun storageObjectDao(): StorageObjectDao
    abstract fun operationJournalDao(): OperationJournalDao
    abstract fun permissionGrantDao(): PermissionGrantDao
    abstract fun trashItemDao(): TrashItemDao
}
