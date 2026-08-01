package com.mammouthclient.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import com.mammouthclient.app.data.Attachment
import com.mammouthclient.app.data.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object FileUtils {

    private const val MAX_IMAGE_SIDE = 1400
    private const val JPEG_QUALITY = 85
    private const val MAX_PDF_PAGES = 12
    private const val MAX_TEXT_CHARS = 120_000

    private fun attachmentsDir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
            }
        }
        return name ?: uri.lastPathSegment ?: "fichier"
    }

    /**
     * Convertit un document choisi par l'utilisateur en une ou plusieurs pièces jointes :
     * image redimensionnée, pages de PDF rendues en images, ou texte brut.
     */
    suspend fun importUri(context: Context, uri: Uri): List<Attachment> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(context, uri)
        when {
            mime.startsWith("image/") -> listOfNotNull(importImage(context, uri, name))
            mime == "application/pdf" || name.endsWith(".pdf", true) -> importPdf(context, uri, name)
            else -> listOfNotNull(importText(context, uri, name, mime))
        }
    }

    private fun importImage(context: Context, uri: Uri, name: String): Attachment? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val target = File(attachmentsDir(context), "img_${UUID.randomUUID()}.jpg")
        writeJpeg(downscale(bitmap), target)
        Attachment(
            name = name,
            mime = "image/jpeg",
            path = target.absolutePath,
            kind = AttachmentKind.IMAGE,
            sizeBytes = target.length()
        )
    }.getOrNull()

    private fun importPdf(context: Context, uri: Uri, name: String): List<Attachment> = runCatching {
        val cached = File(context.cacheDir, "pdf_${UUID.randomUUID()}.pdf")
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(cached).use { output -> input?.copyTo(output) }
        }
        val result = mutableListOf<Attachment>()
        ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pages = min(renderer.pageCount, MAX_PDF_PAGES)
                for (index in 0 until pages) {
                    renderer.openPage(index).use { page ->
                        val scale = MAX_IMAGE_SIDE.toFloat() / max(page.width, page.height)
                        val width = max(1, (page.width * min(scale, 2f)).toInt())
                        val height = max(1, (page.height * min(scale, 2f)).toInt())
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val target = File(attachmentsDir(context), "pdf_${UUID.randomUUID()}.jpg")
                        writeJpeg(bitmap, target)
                        result += Attachment(
                            name = "$name — p.${index + 1}",
                            mime = "image/jpeg",
                            path = target.absolutePath,
                            kind = AttachmentKind.IMAGE,
                            sizeBytes = target.length()
                        )
                    }
                }
            }
        }
        cached.delete()
        result
    }.getOrDefault(emptyList())

    private fun importText(context: Context, uri: Uri, name: String, mime: String): Attachment? =
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?.take(MAX_TEXT_CHARS)
                .orEmpty()
            if (text.isBlank()) return null
            Attachment(
                name = name,
                mime = mime.ifBlank { "text/plain" },
                kind = AttachmentKind.TEXT,
                text = text,
                sizeBytes = text.length.toLong()
            )
        }.getOrNull()

    /** Enregistre un bitmap dans le dossier des pièces jointes (images générées). */
    fun saveGeneratedImage(context: Context, bitmap: Bitmap): String? = runCatching {
        val target = File(attachmentsDir(context), "gen_${UUID.randomUUID()}.jpg")
        writeJpeg(bitmap, target)
        target.absolutePath
    }.getOrNull()

    /** Copie une image dans la galerie de l'appareil. */
    fun exportToGallery(context: Context, path: String): Boolean = runCatching {
        val bitmap = BitmapFactory.decodeFile(path) ?: return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mammouth_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mammouth")
            }
        }
        val uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        true
    }.getOrDefault(false)

    fun toBase64DataUrl(path: String): String? = runCatching {
        val bytes = File(path).readBytes()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    fun decodeBase64Image(context: Context, base64: String): String? = runCatching {
        val clean = base64.substringAfter("base64,", base64)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val target = File(attachmentsDir(context), "gen_${UUID.randomUUID()}.jpg")
        target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()

    fun loadBitmap(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

    private fun writeJpeg(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_IMAGE_SIDE) return bitmap
        val ratio = MAX_IMAGE_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * ratio).toInt()),
            max(1, (bitmap.height * ratio).toInt()),
            true
        )
    }
}
