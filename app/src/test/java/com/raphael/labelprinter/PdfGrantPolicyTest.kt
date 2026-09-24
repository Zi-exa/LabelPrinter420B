package com.raphael.labelprinter

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PdfGrantPolicyTest {
    @Test
    fun temporaryReadGrantCannotBePersisted() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        assertFalse(PdfGrantPolicy.canPersistReadGrant(flags))
    }

    @Test
    fun replacingPersistedPdfReleasesPreviousGrant() {
        val update = PdfGrantPolicy.acceptSelection(
            previousPersistedUri = "content://documents/old",
            newUri = "content://documents/new",
            newUriIsPersisted = true,
        )

        assertEquals("content://documents/new", update.persistedUri)
        assertEquals(listOf("content://documents/old"), update.releaseUris)
    }
}
