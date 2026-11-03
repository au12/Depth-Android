package com.depth.breathtrainer.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TrainerMode
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the generated artwork tiles as content:// URIs. Android Auto fetches and caches
 * art by URI, which is far lighter than pushing a bitmap through the media session on every
 * one-second metadata update. Read-only, and it only ever serves the app's own generated
 * images.
 */
class ArtworkProvider : ContentProvider() {

    private val artwork by lazy { Artwork(context!!) }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val segs = uri.pathSegments
        val bitmap = runCatching {
            when (segs.firstOrNull()) {
                "phase" -> artwork.forPhase(
                    kind = segs[1].takeIf { it != "none" }?.let { PhaseKind.valueOf(it) },
                    word = Uri.decode(segs[2]),
                    mode = TrainerMode.valueOf(segs[3])
                )
                "mode" -> artwork.forMode(TrainerMode.valueOf(segs[1]))
                else -> null
            }
        }.getOrNull() ?: throw FileNotFoundException(uri.toString())

        val dir = File(context!!.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, segs.joinToString("_") { it.filter(Char::isLetterOrDigit) } + ".png")
        if (!file.exists()) {
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        fun authority(context: Context) = "${context.packageName}.artwork"

        fun phaseUri(context: Context, kind: PhaseKind?, word: String, mode: TrainerMode): Uri =
            Uri.Builder().scheme("content").authority(authority(context))
                .appendPath("phase").appendPath(kind?.name ?: "none").appendPath(word).appendPath(mode.name)
                .build()

        fun modeUri(context: Context, mode: TrainerMode): Uri =
            Uri.Builder().scheme("content").authority(authority(context))
                .appendPath("mode").appendPath(mode.name).build()
    }
}
