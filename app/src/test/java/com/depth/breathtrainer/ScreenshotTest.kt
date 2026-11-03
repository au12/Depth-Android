package com.depth.breathtrainer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.depth.breathtrainer.engine.BreathTimerEngine
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.playback.Artwork
import com.depth.breathtrainer.ui.SettingsSheet
import com.depth.breathtrainer.ui.TrainerScreen
import com.depth.breathtrainer.ui.theme.DepthTheme
import com.depth.breathtrainer.ui.theme.Surface
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real app screens with Robolectric's native graphics and writes PNGs to
 * docs/screenshots. Run with `./gradlew screenshots` (or as part of the unit tests).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val outDir = File(System.getProperty("depth.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @After fun stop() = BreathTimerEngine.pause()

    /**
     * Lets color transitions settle, then draws the window into a bitmap. (Compose's
     * captureToImage needs PixelCopy, which Robolectric doesn't implement.)
     */
    private fun save(name: String) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1500)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen() = compose.setContent { DepthTheme { TrainerScreen() } }

    @Test fun ready() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2))
        screen(); save("phone-ready")
    }

    @Test fun hold() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2))
        val e = BreathTimerEngine
        e.start(); repeat(5) { e.skipPhase() } // prep, R1 hold, R1 rest, R2 hold, R2 rest → R3 hold
        repeat(38) { e.tickForTest() }
        screen(); save("phone-hold")
    }

    @Test fun breatheApproachingHold() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2))
        val e = BreathTimerEngine
        e.start(); repeat(2) { e.skipPhase() }
        repeat(112) { e.tickForTest() } // 8s left of the rest before round 2's hold
        screen(); save("phone-hold-approaching")
    }

    @Test fun breathe() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.O2))
        val e = BreathTimerEngine
        e.start(); repeat(4) { e.skipPhase() }
        repeat(47) { e.tickForTest() }
        e.pause()
        screen(); save("phone-breathe-paused")
    }

    @Test fun complete() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.SQUARE, sqCycles = 6))
        val e = BreathTimerEngine
        e.start(); while (!e.uiState.value.complete) e.skipPhase()
        screen(); save("phone-complete")
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun landscape() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2))
        val e = BreathTimerEngine
        e.start(); repeat(3) { e.skipPhase() }
        repeat(21) { e.tickForTest() }
        screen(); save("phone-landscape")
    }

    @Test fun settings() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2))
        compose.setContent {
            DepthTheme { Box(Modifier.fillMaxSize().background(Surface)) { SettingsSheet(BreathTimerEngine.settings.value) {} } }
        }
        save("phone-settings")
    }

    /** The art tiles Android Auto shows next to the title and in the browse list. */
    @Test fun carArtwork() {
        val art = Artwork(context)
        for (mode in TrainerMode.values()) {
            File(outDir, "car-tile-${mode.name.lowercase()}.png").outputStream().use {
                art.forMode(mode).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        listOf(
            Triple(com.depth.breathtrainer.model.PhaseKind.HOLD, "Hold", "car-art-hold"),
            Triple(com.depth.breathtrainer.model.PhaseKind.REST, "Breathe", "car-art-breathe"),
            Triple(com.depth.breathtrainer.model.PhaseKind.PREP, "Get ready", "car-art-prep")
        ).forEach { (k, w, n) ->
            File(outDir, "$n.png").outputStream().use { art.forPhase(k, w, TrainerMode.CO2).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
