package com.virtualworkspace.domain.model

/** Nó virtual com o StorageObject associado (null para pastas). */
data class NodeWithStorage(
    val node: VirtualNode,
    val storageObject: StorageObject?
)
