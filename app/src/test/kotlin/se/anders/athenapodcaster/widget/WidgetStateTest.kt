package se.anders.athenapodcaster.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test fun nothingLoadedWhenIdleOrBlank() {
        assertFalse(WidgetState().loaded)
        assertFalse(WidgetState(title = " ").loaded)
    }

    @Test fun loadedWithATitle() = assertTrue(WidgetState(title = "E1", playing = true).loaded)
}
