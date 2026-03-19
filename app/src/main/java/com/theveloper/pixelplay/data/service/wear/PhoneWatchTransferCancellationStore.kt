package com.theveloper.pixelplay.data.service.wear

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneWatchTransferCancellationStore @Inject constructor() {
    private val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    fun markCancelled(requestId: String) {
        if (requestId.isNotBlank()) {
            cancelledRequestIds.add(requestId)
        }
    }

    fun consumeCancellation(requestId: String): Boolean {
        return requestId.isNotBlank() && cancelledRequestIds.remove(requestId)
    }

    fun clear(requestId: String) {
        cancelledRequestIds.remove(requestId)
    }
}
