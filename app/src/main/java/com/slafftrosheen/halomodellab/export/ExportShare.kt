package com.slafftrosheen.halomodellab.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.slafftrosheen.halomodellab.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shares only the generated archive through a content:// URI.
 *
 * Export files live in private cache; nothing else from the app is exposed. Copying to a
 * user-picked destination is a plain byte copy, so a failed write can leave a partial file:
 * the caller must report that failure instead of claiming success.
 */
object ExportShare {

    fun share(context: Context, file: File) {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_share_title)),
        )
    }

    suspend fun copyToDocument(context: Context, file: File, target: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(target)
                    ?: return@withContext false
                output.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                true
            } catch (failure: Exception) {
                false
            }
        }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )

    const val MIME_TYPE: String = "application/zip"
    const val AUTHORITY_SUFFIX: String = ".exports"
}
