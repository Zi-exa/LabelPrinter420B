package com.raphael.labelprinter

import org.junit.Assert.assertEquals
import org.junit.Test

class UiHandoffTest {
    @Test
    fun queuedUpdateIsDeliveredToReplacementActivity() {
        val handoff = UiHandoff<MutableList<String>>()
        val oldActivity = mutableListOf<String>()
        val replacementActivity = mutableListOf<String>()

        handoff.attach(oldActivity)
        handoff.detach(oldActivity)
        handoff.deliver { it += "print complete" }
        handoff.attach(replacementActivity)

        assertEquals(emptyList<String>(), oldActivity)
        assertEquals(listOf("print complete"), replacementActivity)
    }
}
