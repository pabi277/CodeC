package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.1 — the cold start is CodeC's own. Source scan over the real files:
 * the theme, the manifest, the colour that theme uses, and the ONE new
 * dependency — plus the pin that the splash is released by the policy and
 * never by a clock.
 */
class SplashThemeTest {

    private fun text(path: String): String = RepoFiles.mainSource(path).readText()

    private val themes = "app/src/main/res/values/themes.xml"
    private val colors = "app/src/main/res/values/colors.xml"
    private val manifest = "app/src/main/AndroidManifest.xml"

    @Test
    fun `a splash style exists and is the platform's own backport`() {
        val style = text(themes)
        assertTrue(
            "Theme.Codec.Splash must parent the compat library's Theme.SplashScreen",
            style.contains("<style name=\"Theme.Codec.Splash\" parent=\"Theme.SplashScreen\">"),
        )
    }

    @Test
    fun `the splash background is the brand surface the launcher art already uses`() {
        val style = text(themes)
        assertTrue(
            "the splash must use the one splash colour, not a fresh literal",
            style.contains("<item name=\"windowSplashScreenBackground\">@color/codec_splash_background</item>"),
        )
        val color = text(colors)
        val declared = Regex("""<color name="codec_splash_background">(#\w+)</color>""")
            .find(color)?.groupValues?.get(1)
        assertEquals("#FF101418", declared)
        // ... and the same value the launcher art carries (Phase 38.1): one
        // brand surface, two places that must agree.
        val launcher = text("app/src/main/res/drawable/ic_launcher_background.xml")
        assertTrue(
            "the launcher background must still be the same brand surface",
            launcher.lowercase().contains("101418"),
        )
    }

    @Test
    fun `the splash icon is the asset that already exists`() {
        val style = text(themes)
        assertTrue(
            "windowSplashScreenAnimatedIcon must reuse ic_launcher_foreground",
            style.contains("<item name=\"windowSplashScreenAnimatedIcon\">@drawable/ic_launcher_foreground</item>"),
        )
        assertTrue(
            "the launcher foreground is a real drawable in the tree",
            RepoFiles.mainSource("app/src/main/res/drawable/ic_launcher_foreground.xml").isFile,
        )
    }

    @Test
    fun `the splash hands over to the app theme the app has always used`() {
        val style = text(themes)
        assertTrue(
            "postSplashScreenTheme must be Theme.MyApplication",
            style.contains("<item name=\"postSplashScreenTheme\">@style/Theme.MyApplication</item>"),
        )
        assertTrue(
            "Theme.MyApplication must still exist",
            style.contains("<style name=\"Theme.MyApplication\" parent=\"android:Theme.DeviceDefault.NoActionBar\" />"),
        )
    }

    @Test
    fun `the launcher activity is the one wearing the splash theme`() {
        val xml = text(manifest)
        val activity = xml.substringAfter("<activity").substringBefore("</activity>")
        assertTrue(
            "the launcher activity must be themed by the splash, or nothing shows",
            activity.contains("android:theme=\"@style/Theme.Codec.Splash\""),
        )
        val application = xml.substringAfter("<application").substringBefore(">")
        assertFalse(
            "the application keeps the plain theme; only the launcher activity splashes",
            application.contains("Theme.Codec.Splash"),
        )
    }

    @Test
    fun `the splash style introduces no new colour literal`() {
        val style = text(themes).replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            "no hex may be typed into the theme",
            Regex("""#[0-9A-Fa-f]{6,8}""").containsMatchIn(style),
        )
    }

    @Test
    fun `the one new dependency is recorded in the catalog and the module`() {
        val catalog = RepoFiles.mainSource("gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("coreSplashscreen = \"1.0.1\""))
        assertTrue(
            catalog.contains(
                "androidx-core-splashscreen = { group = \"androidx.core\", name = \"core-splashscreen\", version.ref = \"coreSplashscreen\" }"
            ),
        )
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        assertEquals(
            1,
            Regex("""implementation\(libs\.androidx\.core\.splashscreen\)""").findAll(gradle).count(),
        )
    }

    @Test
    fun `the splash is released by the pure policy, never by a clock`() {
        val readme = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
        assertTrue(readme.contains("installSplashScreen()"))
        assertTrue(readme.contains("setKeepOnScreenCondition { launchGate.keepSplash() }"))
        assertFalse(
            "no artificial minimum display time: no timer may hold the splash",
            Regex("""postDelayed|delay\(\d""").containsMatchIn(
                readme.substringAfter("installSplashScreen()").substringBefore("super.onCreate")
            ),
        )
        val policy = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/crash/LaunchReadiness.kt").readText()
        assertFalse("the policy itself may not know what time it is",
            policy.contains("currentTimeMillis") || policy.contains("nanoTime") || policy.contains("delay("))
    }
}
