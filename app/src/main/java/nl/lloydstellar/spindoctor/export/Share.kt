package nl.lloydstellar.spindoctor.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** An app the user can send a video to with one tap. */
data class ShareTarget(val label: String, val packageName: String)

/**
 * Sharing from the export screen: the Android share sheet, and shortcuts to
 * the social apps that are actually installed. Instagram then asks itself
 * whether it becomes a Reel, a Story or a post.
 */
object Share {
    private val KNOWN = listOf(
        ShareTarget("INSTAGRAM", "com.instagram.android"),
        ShareTarget("TIKTOK", "com.zhiliaoapp.musically"),
        ShareTarget("TIKTOK", "com.ss.android.ugc.trill"),
        ShareTarget("YOUTUBE", "com.google.android.youtube"),
        ShareTarget("FACEBOOK", "com.facebook.katana"),
        ShareTarget("WHATSAPP", "com.whatsapp"),
    )

    fun installedTargets(ctx: Context, multiple: Boolean): List<ShareTarget> {
        val probe = intent(listOf(Uri.EMPTY), multiple)
        val handlers = ctx.packageManager
            .queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }
            .toSet()
        return KNOWN.filter { it.packageName in handlers }.distinctBy { it.label }
    }

    fun send(ctx: Context, uris: List<Uri>, target: ShareTarget? = null) {
        if (uris.isEmpty()) return
        val send = intent(uris, uris.size > 1).apply {
            clipData = ClipData.newRawUri("", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (target != null) setPackage(target.packageName)
        }
        val launch = if (target == null) Intent.createChooser(send, if (uris.size > 1) "Share videos" else "Share video") else send
        try {
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            if (target != null) send(ctx, uris, null)
        }
    }

    fun open(ctx: Context, uri: Uri) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }

    private fun intent(uris: List<Uri>, multiple: Boolean) = if (multiple) {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uris[0])
        }
    }
}
