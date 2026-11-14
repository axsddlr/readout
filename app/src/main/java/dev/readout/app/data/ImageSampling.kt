package dev.readout.app.data

/**
 * The JPEG sample factor for a decoded frame whose longest side is [longest], keeping that side at
 * or under [maxLongest].
 *
 * The card and the fullscreen view want different answers from the same stream: a 1080p frame is
 * wasteful to hold at full size behind a card a few hundred dp wide, and wasteful to halve when it
 * fills the screen. Sampling by powers of two is what [android.graphics.BitmapFactory] can do
 * without a second full-size allocation.
 */
fun sampleSizeFor(longest: Int, maxLongest: Int): Int {
    if (longest <= 0 || maxLongest <= 0) return 1
    var sample = 1
    while (longest / sample > maxLongest) sample *= 2
    return sample
}
