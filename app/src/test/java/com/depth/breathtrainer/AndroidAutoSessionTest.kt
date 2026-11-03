package com.depth.breathtrainer

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.depth.breathtrainer.engine.BreathTimerEngine
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.playback.ArtworkProvider
import com.depth.breathtrainer.playback.DepthMediaService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/**
 * Drives Depth the way the Android Auto host does once it has found the app: it calls the
 * browser service for a root and children, then talks to the media session through a
 * MediaController — reading metadata/playback state for the now-playing screen and sending
 * transport controls when the driver taps buttons.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoSessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var controller: ServiceController<DepthMediaService>
    private lateinit var service: DepthMediaService
    private lateinit var controls: Controls

    private val gearhead = "com.google.android.projection.gearhead"

    @Before
    fun setUp() {
        BreathTimerEngine.resetForTest(context, TrainerSettings(mode = TrainerMode.CO2, rounds = 8))
        controller = Robolectric.buildService(DepthMediaService::class.java).create()
        service = controller.get()
        controls = Controls(service.callback)
        idle()
    }

    @After
    fun tearDown() {
        BreathTimerEngine.pause()
        controller.destroy()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private val metadata get() = service.publishedMetadata!!
    private val playback get() = service.publishedState!!
    private val title get() = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
    private val subtitle get() = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
    private val duration get() = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

    /**
     * The transport-control calls Android Auto makes. Robolectric's MediaSession shadow
     * doesn't relay a session to an in-process MediaController, so these go straight to the
     * session's registered callback — the same object the system binder dispatches to.
     */
    private class Controls(private val cb: android.support.v4.media.session.MediaSessionCompat.Callback) {
        val transportControls get() = this
        fun play() = cb.onPlay()
        fun pause() = cb.onPause()
        fun skipToNext() = cb.onSkipToNext()
        fun skipToPrevious() = cb.onSkipToPrevious()
        fun playFromMediaId(id: String, extras: Bundle?) = cb.onPlayFromMediaId(id, extras)
    }

    @Test
    fun rootIsOfferedToAndroidAutoButNotForAutoResume() {
        val root = service.onGetRoot(gearhead, 10000, null)
        assertNotNull(root)
        assertEquals(DepthMediaService.ROOT_ID, root!!.rootId)
        assertEquals(true, root.extras.getBoolean(DepthMediaService.CONTENT_STYLE_SUPPORTED))

        val recent = Bundle().apply { putBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT, true) }
        assertNull("Must not offer playback resumption (would auto-start a table)", service.onGetRoot(gearhead, 10000, recent))
    }

    @Test
    fun browseListsTheFourTablesAsPlayableItems() {
        val items = service.browseItems()
        assertEquals(listOf("CO2 Table", "O2 Table", "Square Breathing", "Custom Table"), items.map { it.description.title.toString() })
        assertTrue(items.all { it.isPlayable })
        assertEquals("8 rounds · 22:10", items[0].description.subtitle.toString())
        assertTrue(items.all { it.description.iconUri.toString().startsWith("content://${context.packageName}.artwork/") })
    }

    @Test
    fun readyStateIsShownBeforePlay() {
        assertEquals(PlaybackStateCompat.STATE_PAUSED, playback.state)
        assertEquals("Ready · CO2 Table", title)
        assertTrue(playback.actions and PlaybackStateCompat.ACTION_PLAY != 0L)
        assertTrue(playback.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
    }

    @Test
    fun playFromBrowsePicksTheTableAndStartsTheTimer() {
        controls.transportControls.playFromMediaId(DepthMediaService.mediaIdFor(TrainerMode.O2), null)
        idle()
        assertEquals(TrainerMode.O2, BreathTimerEngine.settings.value.mode)
        assertEquals(PlaybackStateCompat.STATE_PLAYING, playback.state)
        assertEquals(1f, playback.playbackSpeed)
        assertEquals("Get ready · 0:10", title)
        assertEquals(10_000L, duration) // progress bar spans the current phase
    }

    @Test
    fun transportControlsMapToTimerControls() {
        controls.transportControls.play(); idle()
        assertEquals(PlaybackStateCompat.STATE_PLAYING, playback.state)

        controls.transportControls.skipToNext(); idle() // next = skip phase
        assertEquals("Hold · 1:30", title)        // CO2: 50% of a 3:00 max
        assertEquals("CO2 · Round 1 of 8", subtitle)
        assertEquals(90_000L, duration)

        repeat(5) { BreathTimerEngine.tickForTest() }; idle()
        assertEquals("Hold · 1:25", title)
        assertEquals(5_000L, playback.position)

        controls.transportControls.pause(); idle()
        assertEquals(PlaybackStateCompat.STATE_PAUSED, playback.state)
        assertTrue(subtitle.startsWith("Paused ·"))

        controls.transportControls.skipToPrevious(); idle() // previous = reset table
        assertEquals("Ready · CO2 Table", title)
    }

    @Test
    fun leadInAnnouncesTheUpcomingHoldOnTheCarScreen() {
        controls.transportControls.play(); idle()
        // 10s prep counts down; at 10s left of a breathing phase the subtitle announces the hold.
        controls.transportControls.skipToNext(); idle() // Hold
        controls.transportControls.skipToNext(); idle() // Breathe / Rest, 2:00
        assertEquals("Breathe · 2:00", title)
        val flashesBefore = BreathTimerEngine.flashTrigger.value
        repeat(110) { BreathTimerEngine.tickForTest() }; idle()
        assertEquals("Breath hold in 10s · CO2", subtitle)
        repeat(3) { BreathTimerEngine.tickForTest() }; idle()
        assertEquals("Breath hold in 7s · CO2", subtitle)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertTrue("10s mark should flash 4 times", BreathTimerEngine.flashTrigger.value - flashesBefore >= 4)
    }

    @Test
    fun artworkIsServedAsDecodablePng() {
        val uri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        assertNotNull(uri)
        Robolectric.setupContentProvider(ArtworkProvider::class.java, ArtworkProvider.authority(context))
        val fd = context.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")!!
        val bmp = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
        fd.close()
        assertNotNull(bmp)
        assertEquals(320, bmp.width)
    }
}
