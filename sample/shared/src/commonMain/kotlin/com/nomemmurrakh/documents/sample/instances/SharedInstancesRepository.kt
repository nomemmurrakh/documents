package com.nomemmurrakh.documents.sample.instances

import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UPDATES_PER_INSTANCE = 100

class SharedInstancesRepository {
    val instanceA: Document<SharedNote> = Documents.document("shared-note")
    val instanceB: Document<SharedNote> = Documents.document("shared-note")

    val areSameObject: Boolean = instanceA === instanceB

    suspend fun addFromBoth(): Unit = withContext(Dispatchers.Default) {
        listOf(instanceA, instanceB).map { instance ->
            launch {
                repeat(UPDATES_PER_INSTANCE) {
                    instance.update { current -> current.copy(count = current.count + 1) }
                }
            }
        }.joinAll()
    }

    fun reset() {
        instanceA.delete()
    }
}
