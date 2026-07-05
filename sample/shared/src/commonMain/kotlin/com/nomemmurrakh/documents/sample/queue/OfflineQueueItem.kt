package com.nomemmurrakh.documents.sample.queue

import kotlinx.serialization.Serializable

@Serializable
data class OfflineQueueItem(
    val id: String,
    val endpoint: String,
    val payloadJson: String,
    val attempts: Int = 0,
)

@Serializable
data class QueueState(
    val items: List<OfflineQueueItem> = emptyList(),
)
