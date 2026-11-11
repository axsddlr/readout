package dev.readout.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {

    @Test
    fun `selecting a tab from a detail screen drops the detail`() {
        assertEquals(
            listOf(Route.Files),
            Nav.select(listOf(Route.Files, Route.Timelapses), Route.Files)
        )
        assertEquals(
            listOf(Route.Machine),
            Nav.select(listOf(Route.Now, Route.Settings), Route.Machine)
        )
    }

    @Test
    fun `selecting the tab you are already on at its root is a no-op`() {
        val stack = listOf(Route.Now)
        assertSame(stack, Nav.select(stack, Route.Now))
    }

    @Test
    fun `push appends a detail and never stacks the same detail twice`() {
        assertEquals(listOf(Route.Files, Route.Timelapses), Nav.push(listOf(Route.Files), Route.Timelapses))
        val stack = listOf(Route.Files, Route.Timelapses)
        assertSame(stack, Nav.push(stack, Route.Timelapses))
    }

    @Test
    fun `back pops one detail and reports nothing to do at the root`() {
        assertEquals(
            listOf(Route.Now),
            Nav.back(listOf(Route.Now, Route.Settings))
        )
        assertNull(Nav.back(listOf(Route.Now)))
        assertNull(Nav.back(emptyList()))
    }

    @Test
    fun `bottom bar in portrait, rail when the window is wide or short`() {
        assertEquals(NavSurface.BottomBar, Nav.surface(widthDp = 411, heightDp = 891))
        assertEquals(NavSurface.BottomBar, Nav.surface(widthDp = 360, heightDp = 800))
        // Landscape phone: a bottom bar would eat a fifth of the height.
        assertEquals(NavSurface.Rail, Nav.surface(widthDp = 891, heightDp = 411))
        // Tablet portrait: wide enough for the rail to fit alongside content.
        assertEquals(NavSurface.Rail, Nav.surface(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun `only the three tabs are top level`() {
        assertTrue(Route.Now.isTopLevel)
        assertTrue(Route.Files.isTopLevel)
        assertTrue(Route.Machine.isTopLevel)
        assertFalse(Route.Settings.isTopLevel)
        assertFalse(Route.Timelapses.isTopLevel)
        assertFalse(Route.Fleet.isTopLevel)
        assertFalse(Route.Preview.isTopLevel)
    }
}
