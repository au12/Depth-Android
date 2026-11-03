package com.depth.breathtrainer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.depth.breathtrainer.engine.BreathTimerEngine
import com.depth.breathtrainer.engine.TableBuilder
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.playback.NowPlayingMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableAndMappingTest {

    @Test
    fun co2TableHoldsConstantAndRestsShrink() {
        val r = TableBuilder.buildRounds(TrainerSettings(mode = TrainerMode.CO2, rounds = 8))
        assertEquals(8, r.size)
        assertTrue(r.all { it.hold == 90 })
        assertEquals(120, r.first().rest)
        assertEquals(30, r.last().rest)
    }

    @Test
    fun o2TableHoldsGrowAndRestIsConstant() {
        val r = TableBuilder.buildRounds(TrainerSettings(mode = TrainerMode.O2, rounds = 8))
        assertEquals(90, r.first().hold)
        assertEquals(171, r.last().hold)
        assertTrue(r.all { it.rest == 120 })
    }

    @Test
    fun squareSkipsZeroLengthSides() {
        val s = TrainerSettings(mode = TrainerMode.SQUARE, sqHold2 = 0, sqCycles = 2, prepOn = false)
        val phases = TableBuilder.flatten(TableBuilder.buildRounds(s), s.mode, s.prepOn)
        assertEquals(listOf("INHALE", "HOLD", "EXHALE", "INHALE", "HOLD", "EXHALE"), phases.map { it.label })
    }

    @Test
    fun customParsesLinesAndIgnoresBlanks() {
        val s = TrainerSettings(mode = TrainerMode.CUSTOM, customRounds = "60,120\n\n 75 , 90 \n")
        assertEquals(2, TableBuilder.buildRounds(s).size)
    }

    @Test
    fun engineCuesAndLeadIn() {
        BreathTimerEngine.resetForTest(ApplicationProvider.getApplicationContext(), TrainerSettings(rounds = 2))
        val e = BreathTimerEngine
        e.start()
        assertEquals(PhaseKind.PREP, e.uiState.value.current!!.kind)
        assertTrue(e.uiState.value.holdApproaching) // 10s prep → hold next
        repeat(10) { e.tickForTest() }
        assertEquals(PhaseKind.HOLD, e.uiState.value.current!!.kind)
        assertEquals(90, e.uiState.value.remainingSeconds)
        assertFalse(e.uiState.value.holdApproaching)
        e.pause()
    }

    @Test
    fun completeStateMapsToSessionComplete() {
        BreathTimerEngine.resetForTest(ApplicationProvider.getApplicationContext(), TrainerSettings(mode = TrainerMode.SQUARE, sqCycles = 1, prepOn = false))
        val e = BreathTimerEngine
        e.start()
        repeat(4) { e.skipPhase() }
        assertTrue(e.uiState.value.complete)
        assertFalse(e.uiState.value.running)
        assertEquals("Session complete", NowPlayingMapper.map(e.uiState.value, e.settings.value).title)
        // Play after completion restarts the table.
        e.start()
        assertTrue(e.uiState.value.running)
        assertEquals(0, e.uiState.value.phaseIndex)
        e.pause()
    }
}
