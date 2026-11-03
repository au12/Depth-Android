# The media browser service is bound by Android Auto by class name from the manifest.
-keep class com.depth.breathtrainer.playback.DepthMediaService { *; }
-keep class androidx.media.MediaBrowserServiceCompat { *; }
