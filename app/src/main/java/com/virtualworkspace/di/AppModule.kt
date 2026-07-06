package com.virtualworkspace.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.virtualworkspace.data.db.OperationJournalDao
import com.virtualworkspace.data.db.PermissionGrantDao
import com.virtualworkspace.data.db.StorageObjectDao
import com.virtualworkspace.data.db.TrashItemDao
import com.virtualworkspace.data.db.VirtualNodeDao
import com.virtualworkspace.data.db.WorkspaceDatabase
import com.virtualworkspace.data.db.WorkspaceRootDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkspaceDatabase =
        Room.databaseBuilder(context, WorkspaceDatabase::class.java, "workspace.db")
            .build()

    @Provides fun provideWorkspaceRootDao(db: WorkspaceDatabase): WorkspaceRootDao = db.workspaceRootDao()
    @Provides fun provideVirtualNodeDao(db: WorkspaceDatabase): VirtualNodeDao = db.virtualNodeDao()
    @Provides fun provideStorageObjectDao(db: WorkspaceDatabase): StorageObjectDao = db.storageObjectDao()
    @Provides fun provideOperationJournalDao(db: WorkspaceDatabase): OperationJournalDao = db.operationJournalDao()
    @Provides fun providePermissionGrantDao(db: WorkspaceDatabase): PermissionGrantDao = db.permissionGrantDao()
    @Provides fun provideTrashItemDao(db: WorkspaceDatabase): TrashItemDao = db.trashItemDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
