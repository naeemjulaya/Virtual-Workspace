package com.virtualworkspace.di

import com.virtualworkspace.data.repository.OperationJournalImpl
import com.virtualworkspace.data.repository.PermissionManagerImpl
import com.virtualworkspace.data.repository.StorageRepositoryImpl
import com.virtualworkspace.data.repository.TrashManagerImpl
import com.virtualworkspace.data.repository.WorkspaceRepositoryImpl
import com.virtualworkspace.data.storage.PhysicalStorageGatewayImpl
import com.virtualworkspace.domain.repository.OperationJournal
import com.virtualworkspace.domain.repository.PermissionManager
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.TrashManager
import com.virtualworkspace.domain.repository.WorkspaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindWorkspaceRepository(impl: WorkspaceRepositoryImpl): WorkspaceRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    abstract fun bindPermissionManager(impl: PermissionManagerImpl): PermissionManager

    @Binds
    abstract fun bindOperationJournal(impl: OperationJournalImpl): OperationJournal

    @Binds
    abstract fun bindTrashManager(impl: TrashManagerImpl): TrashManager

    @Binds
    abstract fun bindPhysicalStorageGateway(impl: PhysicalStorageGatewayImpl): PhysicalStorageGateway
}
