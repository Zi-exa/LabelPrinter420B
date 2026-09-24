package com.raphael.labelprinter

internal class UiHandoff<T : Any> {
    private val lock = Any()
    private var target: T? = null
    private val queuedActions = mutableListOf<(T) -> Unit>()

    fun attach(newTarget: T) {
        val actions = synchronized(lock) {
            target = newTarget
            queuedActions.toList().also { queuedActions.clear() }
        }
        actions.forEach { it(newTarget) }
    }

    fun detach(oldTarget: T) {
        synchronized(lock) {
            if (target === oldTarget) target = null
        }
    }

    fun deliver(action: (T) -> Unit) {
        val current = synchronized(lock) {
            target.also {
                if (it == null) queuedActions += action
            }
        }
        if (current != null) action(current)
    }
}
