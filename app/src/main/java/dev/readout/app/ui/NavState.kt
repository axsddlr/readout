package dev.readout.app.ui

/**
 * Navigation state as an explicit back stack, not a set of booleans.
 *
 * Top-level destinations (Now, Files, Machine) replace one another. Detail destinations
 * (Settings, Timelapses, Fleet) are pushed on top and pop back to whatever opened them, so
 * Back always undoes the thing the user just did instead of guessing from a flag.
 */
enum class Route {
    Now, Files, Machine, Settings, Timelapses, Fleet, Preview;

    val isTopLevel: Boolean get() = this == Now || this == Files || this == Machine
}

/** Which navigation surface the window can afford. */
enum class NavSurface { BottomBar, Rail }

/**
 * Pure stack transitions so the rules can be unit-tested without a device. Every function
 * returns a new list; nothing here touches Compose.
 */
object Nav {
    val start: List<Route> = listOf(Route.Now)

    /**
     * Selecting a tab replaces the stack with that tab's root, which also clears any detail
     * screens above it. Re-selecting the tab you are already on at its root changes nothing.
     */
    fun select(stack: List<Route>, route: Route): List<Route> {
        require(route.isTopLevel) { "select() only handles top-level destinations" }
        return if (stack == listOf(route)) stack else listOf(route)
    }

    fun push(stack: List<Route>, route: Route): List<Route> =
        if (stack.lastOrNull() == route) stack else stack + route

    /** Null at the root, so the caller can let the system handle Back (leave the app). */
    fun back(stack: List<Route>): List<Route>? =
        if (stack.size <= 1) null else stack.dropLast(1)

    /**
     * A bottom bar spends vertical space, which short landscape windows have least of; a rail
     * spends width, which a phone portrait window has least of. Compact height or medium-plus
     * width therefore gets the rail.
     */
    fun surface(widthDp: Int, heightDp: Int): NavSurface = when {
        heightDp < 480 -> NavSurface.Rail
        widthDp >= 600 -> NavSurface.Rail
        else -> NavSurface.BottomBar
    }
}
