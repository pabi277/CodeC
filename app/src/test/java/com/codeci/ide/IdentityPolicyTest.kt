package com.codeci.ide

import com.codeci.ide.ui.theme.BrandMode
import com.codeci.ide.ui.theme.IdentityInput
import com.codeci.ide.ui.theme.IdentityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 50.2 — the brand decision, case by case. Owner decision (2026-09-20,
 * chat): brand green by default; the wallpaper only when the user asks.
 */
class IdentityPolicyTest {

    private fun decide(
        stored: String? = null,
        dynamicAvailable: Boolean = true,
        darkTheme: Boolean = true,
        prefersWallpaper: Boolean = false,
    ): BrandMode = IdentityPolicy.decide(
        IdentityInput(stored, dynamicAvailable, darkTheme),
        prefersWallpaper,
    )

    @Test
    fun `a fresh install is the brand`() {
        assertEquals(BrandMode.BRAND, decide())
    }

    @Test
    fun `a stored accent beats everything`() {
        assertEquals(
            BrandMode.BRAND,
            decide(stored = "#FF6200EE", prefersWallpaper = true),
        )
    }

    @Test
    fun `a stored accent beats everything without dynamic`() {
        assertEquals(
            BrandMode.BRAND,
            decide(stored = "#FF6200EE", dynamicAvailable = false, prefersWallpaper = true),
        )
    }

    @Test
    fun `android 7 to 11 is the brand even when the wallpaper is preferred`() {
        assertEquals(
            BrandMode.BRAND,
            decide(dynamicAvailable = false, prefersWallpaper = true),
        )
    }

    @Test
    fun `api 31 with the switch off is the brand`() {
        assertEquals(
            BrandMode.BRAND,
            decide(dynamicAvailable = true, prefersWallpaper = false),
        )
    }

    @Test
    fun `api 31 with the switch on is dynamic`() {
        assertEquals(
            BrandMode.DYNAMIC,
            decide(dynamicAvailable = true, prefersWallpaper = true),
        )
    }

    @Test
    fun `a corrupt stored value still counts as stored`() {
        // Garbage means "chose once" (or a crash wrote junk): the theme
        // falls back to the default seed, but the mode stays brand — the
        // wallpaper must never win by accident.
        assertEquals(
            BrandMode.BRAND,
            decide(stored = "not-a-colour", prefersWallpaper = true),
        )
    }

    @Test
    fun `dark and light never change the mode`() {
        assertEquals(
            decide(darkTheme = true, prefersWallpaper = true),
            decide(darkTheme = false, prefersWallpaper = true),
        )
        assertEquals(
            decide(darkTheme = true),
            decide(darkTheme = false),
        )
    }

    @Test
    fun `every input combination is decided`() {
        // Exhaustion: 2 (stored?) × 2 (available?) × 2 (dark?) × 2 (wants?) —
        // no input throws, and DYNAMIC appears exactly when nothing is
        // stored, dynamic exists, and the user asked.
        for (stored in listOf(null, "#FF3DDC84")) {
            for (available in listOf(false, true)) {
                for (dark in listOf(false, true)) {
                    for (wants in listOf(false, true)) {
                        val mode = decide(stored, available, dark, wants)
                        if (stored == null && available && wants) {
                            assertEquals("DYNAMIC only here", BrandMode.DYNAMIC, mode)
                        } else {
                            assertEquals(
                                "BRAND for stored=$stored available=$available wants=$wants",
                                BrandMode.BRAND,
                                mode,
                            )
                        }
                    }
                }
            }
        }
    }
}
