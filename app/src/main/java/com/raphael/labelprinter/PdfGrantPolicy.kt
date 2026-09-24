package com.raphael.labelprinter

import android.content.Intent

internal data class PdfGrantUpdate(
    val persistedUri: String?,
    val releaseUris: List<String>,
)

internal object PdfGrantPolicy {
    fun canPersistReadGrant(flags: Int): Boolean {
        val required =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        return flags and required == required
    }

    fun acceptSelection(
        previousPersistedUri: String?,
        newUri: String,
        newUriIsPersisted: Boolean,
    ): PdfGrantUpdate {
        val persistedUri = newUri.takeIf { newUriIsPersisted }
        val releaseUris = listOfNotNull(
            previousPersistedUri?.takeIf { it != persistedUri },
        )
        return PdfGrantUpdate(persistedUri, releaseUris)
    }
}
