package com.personal.twelveweek

import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalLinksTest {
    @Test
    fun hermitPlayStoreUrlPointsAtHermitsRealPackageId() {
        assertTrue(HERMIT_PLAY_STORE_URL.startsWith("https://play.google.com/store/apps/details?id="))
        assertTrue(HERMIT_PLAY_STORE_URL.endsWith("com.chimbori.hermitcrab"))
    }
}
