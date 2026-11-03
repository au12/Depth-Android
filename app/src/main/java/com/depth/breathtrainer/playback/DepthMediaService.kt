package com.depth.breathtrainer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.depth.breathtrainer.MainActivity
import com.depth.breathtrainer.R
import com.depth.breathtrainer.engine.BreathTimerEngine
import com.depth.breathtrainer.engine.TableBuilder
import com.depth.breathtrainer.model.TimerUiState
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.model.clock
import com.depth.breathtrainer.model.modeTitle
import com.depth.breathtrainer.model.shortTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Depth's Android Auto entry point.
 *
 * Android Auto lists every installed app that exports a MediaBrowserService and declares
 * `<uses name="media"/>` in automotive_app_desc.xml. When the driver opens Depth it:
 *   1. binds here and calls [onGetRoot], then [onLoadChildren] → the four tables;
 *   2. connects a MediaController to [session] and renders its metadata + playback state
 *      as the now-playing screen: title ("Hold · 1:25"), subtitle, art, and a progress bar
 *      sized to the current phase;
 *   3. sends transport controls: play/pause → timer, next → skip phase, previous → reset.
 *
 * Depth never plays audio through the session and never requests audio focus, so music in
 * another app keeps playing.
 *
 * The same session also drives the phone's playback notification and lock-screen controls.
 */
class DepthMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isForeground = false
    private var lastMetadataKey: String? = null
    private val notificationArt = HashMap<String, Bitmap>()
    private val artwork by lazy { Artwork(this) }

    /** The exact objects handed to the session — what a MediaController (Android Auto) reads. */
    @VisibleForTesting internal var publishedState: PlaybackStateCompat? = null
    @VisibleForTesting internal var publishedMetadata: MediaMetadataCompat? = null
    /** The callback Android Auto's transport controls are dispatched to. */
    @VisibleForTesting internal val callback: MediaSessionCompat.Callback = Callback()

    override fun onCreate() {
        super.onCreate()
        BreathTimerEngine.init(this)
        createChannel()

        session = MediaSessionCompat(this, "Depth").apply {
            setCallback(callback)
            setSessionActivity(openAppIntent())
            isActive = true
        }
        sessionToken = session.sessionToken

        scope.launch {
            combine(BreathTimerEngine.uiState, BreathTimerEngine.settings) { s, set -> s to set }
                .collect { (state, settings) -> publish(state, settings) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Anything started with startForegroundService must enter the foreground promptly.
        goForeground()
        when (intent?.action) {
            ACTION_TOGGLE -> BreathTimerEngine.togglePlayPause()
            ACTION_SKIP -> BreathTimerEngine.skipPhase()
            ACTION_RESET -> BreathTimerEngine.reset()
        }
        if (!BreathTimerEngine.uiState.value.running) leaveForeground()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!BreathTimerEngine.uiState.value.running) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    // ---------- Browsing (what Android Auto lists when you open Depth) ----------

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // "Recent" roots are used for playback resumption on car connect. Declining keeps
        // Android Auto from auto-starting a breath-hold table when the phone plugs in.
        if (rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) return null
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(if (parentId == ROOT_ID) browseItems().toMutableList() else mutableListOf())
    }

    @VisibleForTesting
    internal fun browseItems(): List<MediaBrowserCompat.MediaItem> {
        val settings = BreathTimerEngine.settings.value
        return TrainerMode.values().map { mode ->
            val forMode = settings.copy(mode = mode)
            val phases = TableBuilder.flatten(TableBuilder.buildRounds(forMode), mode, forMode.prepOn)
            val rounds = phases.firstOrNull()?.totalRounds ?: 0
            val unit = if (mode == TrainerMode.SQUARE) "cycles" else "rounds"
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(mediaIdFor(mode))
                .setTitle(modeTitle(mode))
                .setSubtitle("$rounds $unit · ${clock(phases.sumOf { it.seconds })}")
                .setIconUri(ArtworkProvider.modeUri(this, mode))
                .build()
            MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }
    }

    // ---------- Transport controls ----------

    private inner class Callback : MediaSessionCompat.Callback() {
        override fun onPlay() = BreathTimerEngine.start()
        override fun onPause() = BreathTimerEngine.pause()
        override fun onStop() = BreathTimerEngine.pause()
        override fun onSkipToNext() = BreathTimerEngine.skipPhase()
        override fun onSkipToPrevious() = BreathTimerEngine.reset()
        override fun onPlayFromSearch(query: String?, extras: Bundle?) = BreathTimerEngine.start()

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val mode = modeFromMediaId(mediaId) ?: return BreathTimerEngine.start()
            val engine = BreathTimerEngine
            val state = engine.uiState.value
            if (mode == engine.settings.value.mode && state.started && !state.complete) {
                engine.start() // same table already in progress: just resume it
            } else {
                engine.updateSettings(engine.settings.value.copy(mode = mode))
                engine.start()
            }
        }
    }

    // ---------- Publishing state ----------

    private fun publish(state: TimerUiState, settings: TrainerSettings) {
        val np = NowPlayingMapper.map(state, settings)

        val playback =
            PlaybackStateCompat.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(
                    if (np.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    np.positionMs,
                    if (np.playing) 1f else 0f,
                    state.tickStamp
                )
                .setExtras(Bundle().apply {
                    putBoolean(RESERVE_PREVIOUS, true)
                    putBoolean(RESERVE_NEXT, true)
                })
                .build()
        publishedState = playback
        session.setPlaybackState(playback)

        val artWord = if (state.complete || state.current == null) "Done" else state.current!!.shortTitle()
        val artUri = ArtworkProvider.phaseUri(this, np.kind, artWord, settings.mode).toString()
        val key = listOf(np.title, np.subtitle, np.description, np.durationMs, artUri).joinToString("|")
        if (key != lastMetadataKey) {
            lastMetadataKey = key
            val metadata =
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "depth-session")
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, np.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, np.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, np.subtitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, np.subtitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, np.description)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, np.description)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, np.durationMs)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    .build()
            publishedMetadata = metadata
            session.setMetadata(metadata)
        }

        if (state.running && !isForeground) {
            // Keeps the countdown alive with the screen off. If Android refuses (a rare
            // background-start restriction), the timer still runs while Android Auto is bound.
            runCatching { ContextCompat.startForegroundService(this, Intent(this, DepthMediaService::class.java)) }
            goForeground()
        } else if (!state.running && isForeground) {
            leaveForeground()
        }

        val nm = getSystemService(NotificationManager::class.java)
        if (state.started || state.running) {
            runCatching { nm.notify(NOTIFICATION_ID, buildNotification(np, state, settings)) }
        } else if (!isForeground) {
            nm.cancel(NOTIFICATION_ID)
        }
    }

    private fun goForeground() {
        val np = NowPlayingMapper.map(BreathTimerEngine.uiState.value, BreathTimerEngine.settings.value)
        val ok = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID,
                buildNotification(np, BreathTimerEngine.uiState.value, BreathTimerEngine.settings.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
        }.isSuccess
        isForeground = ok
    }

    private fun leaveForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        isForeground = false
    }

    // ---------- Notification ----------

    private fun buildNotification(np: NowPlaying, state: TimerUiState, settings: TrainerSettings): Notification {
        val artKey = "${np.kind}:${settings.mode}:${state.current?.label}"
        val large = notificationArt.getOrPut(artKey) {
            val word = if (state.complete || state.current == null) "Done" else state.current!!.shortTitle()
            Bitmap.createScaledBitmap(artwork.forPhase(np.kind, word, settings.mode), 192, 192, true)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(np.title)
            .setContentText(np.subtitle)
            .setSubText(np.description)
            .setLargeIcon(large)
            .setContentIntent(openAppIntent())
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_reset, "Reset", serviceIntent(ACTION_RESET, 1))
            .addAction(
                if (state.running) R.drawable.ic_pause else R.drawable.ic_play,
                if (state.running) "Pause" else "Start",
                serviceIntent(ACTION_TOGGLE, 2)
            )
            .addAction(R.drawable.ic_skip, "Skip", serviceIntent(ACTION_SKIP, 3))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun serviceIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, DepthMediaService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running breath-hold table"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @VisibleForTesting
    internal fun sessionTokenForTest(): MediaSessionCompat.Token = session.sessionToken

    companion object {
        const val ROOT_ID = "depth_root"
        const val CHANNEL_ID = "depth_session"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE = "com.depth.breathtrainer.TOGGLE"
        const val ACTION_SKIP = "com.depth.breathtrainer.SKIP"
        const val ACTION_RESET = "com.depth.breathtrainer.RESET"

        // Android Auto content-style and slot-reservation keys (documented string constants).
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_LIST = 1
        const val RESERVE_PREVIOUS = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
        const val RESERVE_NEXT = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"

        const val SUPPORTED_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        fun mediaIdFor(mode: TrainerMode) = "table:${mode.name}"
        fun modeFromMediaId(id: String?): TrainerMode? =
            id?.removePrefix("table:")?.let { name -> TrainerMode.values().firstOrNull { it.name == name } }

        /**
         * Called by the phone UI before starting the timer so the countdown keeps running
         * (as a foreground service) when the screen turns off.
         */
        fun ensureStarted(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DepthMediaService::class.java))
            }
        }
    }
}
