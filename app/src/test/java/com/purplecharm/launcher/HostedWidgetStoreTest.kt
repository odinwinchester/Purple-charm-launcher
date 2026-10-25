package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class HostedWidgetStoreTest {
    private fun widget(id: Int = 12) = HostedWidget(id, "clock/Provider", 1, 3, 180, 0)
    @Test fun codecPreservesIdentityPageSizeAndOrderWithoutTouchingIconModels() {
        val list = listOf(widget(), widget(13).copy(page = 2, span = 2, heightDp = 240, order = 1))
        assertEquals(list, HostedWidgetStore.decode(HostedWidgetStore.encode(list)))
        assertTrue(HostedWidgetStore.decode(null).isEmpty())
    }
    @Test fun invalidOrDuplicateIdsAndUnsupportedGeometryAreRejected() {
        assertFalse(HostedWidgetStore.valid(listOf(widget(), widget())))
        for (w in listOf(widget(0), widget().copy(page = 8), widget().copy(span = 0), widget().copy(heightDp = 5), widget().copy(provider = "bad")))
            assertFalse(HostedWidgetStore.valid(listOf(w)))
    }
    @Test fun corruptAndUnknownVersionDataAreNotSilentlyReset() {
        assertThrows(Exception::class.java) { HostedWidgetStore.decode("bad json") }
        assertThrows(Exception::class.java) { HostedWidgetStore.decode("{\"version\":2,\"widgets\":[]}") }
    }
}
