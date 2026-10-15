package nl.lloydstellar.spindoctor.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Puts a finished video in the phone's gallery, under Movies/Spin Doctor.
 * No storage permission needed: an app may always add its own media.
 */
object Gallery {
    const val FOLDER = "Spin Doctor"

    fun save(ctx: Context, file: File, displayName: String): Uri {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("The gallery refused the video")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** "My Track - lloydstellar.nl.mp4", the file name the website gives its downloads. */
    fun fileName(baseName: String): String {
        val clean = baseName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "vinyl-animation" }
        return "$clean - lloydstellar.nl.mp4"
    }
}
