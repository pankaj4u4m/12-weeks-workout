package com.personal.twelveweek

import com.personal.twelveweek.storage.RawKeyFlagStore
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressTest {

    // Same namespace every test uses a fresh RawKeyFlagStore instance for,
    // but the underlying platform storage (SharedPreferences file /
    // localStorage prefix) is real and persistent within a test process —
    // clear it before each store is built so tests don't leak into each other.
    private fun freshStore(): RawKeyFlagStore {
        val store = RawKeyFlagStore("progress_test")
        store.clear()
        return store
    }

    @Test
    fun `setDone then isDone round-trips true`() {
        val progress = ProgressStore(freshStore())
        assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
        progress.setDone("program-1:w1-o1-s0-i0", true)
        assertTrue(progress.isDone("program-1:w1-o1-s0-i0"))
    }

    @Test
    fun `setDone false clears a previously-done key`() {
        val progress = ProgressStore(freshStore())
        progress.setDone("program-1:w1-o1-s0-i0", true)
        progress.setDone("program-1:w1-o1-s0-i0", false)
        assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
    }

    @Test
    fun `toggle flips state`() {
        val progress = ProgressStore(freshStore())
        progress.toggle("k")
        assertTrue(progress.isDone("k"))
        progress.toggle("k")
        assertFalse(progress.isDone("k"))
    }

    @Test
    fun `countDone counts only the done keys among those given`() {
        val progress = ProgressStore(freshStore())
        progress.setDone("a", true)
        progress.setDone("b", true)
        assertEquals(2, progress.countDone(listOf("a", "b", "c")))
    }

    @Test
    fun `setAll marks every key done, then setAll false clears them all`() {
        val progress = ProgressStore(freshStore())
        progress.setAll(listOf("a", "b", "c"), true)
        assertEquals(3, progress.countDone(listOf("a", "b", "c")))
        progress.setAll(listOf("a", "b", "c"), false)
        assertEquals(0, progress.countDone(listOf("a", "b", "c")))
    }

    @Test
    fun `clearEverything wipes all done keys`() {
        val progress = ProgressStore(freshStore())
        progress.setDone("a", true)
        progress.clearEverything()
        assertFalse(progress.isDone("a"))
    }

    @Test
    fun `legacy unprefixed key is honored by the program-1 prefixed lookup`() {
        val store = freshStore()
        store.setPresent("w1-o1-s0-i0") // legacy, pre-program-library key shape
        val progress = ProgressStore(store)
        assertTrue(progress.isDone("program-1:w1-o1-s0-i0"))
    }

    @Test
    fun `writing the new-shape key retires the legacy key so it can't cause a stale read`() {
        val store = freshStore()
        store.setPresent("w1-o1-s0-i0") // legacy
        val progress = ProgressStore(store)
        progress.setDone("program-1:w1-o1-s0-i0", false) // explicitly un-done via the new key
        assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
        // re-construct a fresh ProgressStore over the same underlying store to
        // prove the legacy key was actually retired in the raw store, not just
        // masked in this instance's in-memory state
        assertFalse(ProgressStore(store).isDone("program-1:w1-o1-s0-i0"))
    }
}
