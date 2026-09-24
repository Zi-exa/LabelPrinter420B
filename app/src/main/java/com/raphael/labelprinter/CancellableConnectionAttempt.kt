package com.raphael.labelprinter

import java.io.IOException

internal class ConnectionCancelledException : IOException("Koneksi Bluetooth dibatalkan")

internal class CancellableConnectionAttempt<T : Any>(
    private val close: (T) -> Unit,
) {
    private val lock = Any()
    private var pending: T? = null

    fun connect(
        create: () -> T,
        connect: (T) -> Unit,
    ): T {
        val candidate = create()
        synchronized(lock) {
            check(pending == null) { "Percobaan koneksi lain masih berjalan" }
            pending = candidate
        }

        try {
            connect(candidate)
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (pending === candidate) pending = null
            }
            runCatching { close(candidate) }
            throw failure
        }

        val completed = synchronized(lock) {
            if (pending === candidate) {
                pending = null
                true
            } else {
                false
            }
        }
        if (!completed) {
            runCatching { close(candidate) }
            throw ConnectionCancelledException()
        }
        return candidate
    }

    fun cancel() {
        val candidate = synchronized(lock) {
            pending.also { pending = null }
        }
        if (candidate != null) runCatching { close(candidate) }
    }
}
