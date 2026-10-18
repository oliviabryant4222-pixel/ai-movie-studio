package com.aimoviestudio.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var mediaText: TextView
    private lateinit var exportButton: Button
    private lateinit var root: LinearLayout

    private var audioUri: Uri? = null
    private val photoUris = mutableListOf<Uri>()
    private var exporting = false
    private var activeTransformer: Transformer? = null

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                audioUri = uri
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                updateMediaInfo()
                statusText.text = "Voiceover audio selected. Add photos, then tap Create MP4."
            }
        }

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                photoUris.clear()
                photoUris.addAll(uris.filter { isImage(it) })
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                    }
                }
                updateMediaInfo()
                statusText.text = if (photoUris.isEmpty()) {
                    "Please select image files. Video clips are not supported in this first export version."
                } else {
                    "${photoUris.size} photo(s) selected. Ready to create your video."
                }
            }
        }

    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startExport() else statusText.text = "Storage permission is needed to save the MP4."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(android.graphics.Color.rgb(15, 18, 30))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "AI MOVIE STUDIO"
            textSize = 28f
            setTextColor(android.graphics.Color.rgb(100, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Turn your photos and voiceover into an MP4 movie"
            textSize = 15f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(subtitle)

        addSection("1. VOICEOVER")
        addButton("Choose voiceover audio") { audioPicker.launch(arrayOf("audio/*")) }

        addSection("2. PHOTOS")
        addButton("Choose photos for slideshow") { photoPicker.launch(arrayOf("image/*")) }

        addSection("3. PROJECT FILES")
        mediaText = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(12, 12, 12, 12)
            text = "No files selected yet."
        }
        root.addView(mediaText)

        addSection("4. CREATE YOUR MOVIE")
        addButton("Create MP4 video") {
            if (exporting) {
                Toast.makeText(this, "Video export is already running.", Toast.LENGTH_SHORT).show()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                startExport()
            }
        }.also { exportButton = it }

        statusText = TextView(this).apply {
            text = "Ready. Select voiceover audio and photos to create a slideshow video."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(120, 240, 170))
            setPadding(12, 12, 12, 16)
        }
        root.addView(statusText)

        addButton("Clear selected files") {
            if (exporting) {
                Toast.makeText(this, "Wait for export to finish before clearing.", Toast.LENGTH_SHORT).show()
            } else {
                audioUri = null
                photoUris.clear()
                updateMediaInfo()
                statusText.text = "Selection cleared."
            }
        }

        val note = TextView(this).apply {
            text = "\nFirst movie-maker version: each photo displays for 4 seconds, with your selected audio as the soundtrack. The MP4 is saved in Movies/AI Movie Studio. Video clips, transitions and AI-generated narration will come in later updates."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(8, 20, 8, 20)
        }
        root.addView(note)

        setContentView(scroll)
    }

    private fun addSection(text: String) {
        val heading = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(android.graphics.Color.rgb(100, 220, 255))
            setPadding(0, 22, 0, 8)
        }
        root.addView(heading)
    }

    private fun addButton(label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setOnClickListener { action() }
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        )
        return button
    }

    private fun isImage(uri: Uri): Boolean {
        return contentResolver.getType(uri)?.startsWith("image/") == true
    }

    private fun updateMediaInfo() {
        val lines = mutableListOf(
            "Voiceover: ${if (audioUri != null) audioUri!!.lastPathSegment ?: "Audio selected" else "Not selected"}",
            "",
            "Photos: ${photoUris.size}"
        )
        photoUris.forEachIndexed { index, uri ->
            lines.add("${index + 1}. ${uri.lastPathSegment ?: "Photo"}")
        }
        mediaText.text = lines.joinToString("\n")
    }

    private fun startExport() {
        val soundtrack = audioUri
        if (soundtrack == null) {
            statusText.text = "Choose a voiceover audio file first."
            return
        }
        if (photoUris.isEmpty()) {
            statusText.text = "Choose at least one photo first."
            return
        }

        exporting = true
        exportButton.isEnabled = false
        statusText.text = "Preparing your slideshow… please keep the app open."

        val outputFile = File(cacheDir, "ai_movie_${System.currentTimeMillis()}.mp4")
        val imageItems = photoUris.map { uri ->
            val image = MediaItem.Builder()
                .setUri(uri)
                .setImageDurationMs(4_000)
                .build()
            EditedMediaItem.Builder(image).setFrameRate(30).build()
        }
        val videoSequence = EditedMediaItemSequence.withVideoFrom(imageItems)
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(soundtrack)).build()
        val audioSequence = EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
            .buildUpon()
            .setIsLooping(true)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                try {
                    val savedUri = copyToMovies(outputFile)
                    statusText.text = "Movie created successfully! Saved to Movies/AI Movie Studio."
                    Toast.makeText(this@MainActivity, "MP4 saved to Movies/AI Movie Studio", Toast.LENGTH_LONG).show()
                    if (savedUri != null) statusText.append("\nSaved file: ${savedUri.lastPathSegment ?: "MP4"}")
                } catch (e: Exception) {
                    statusText.text = "Video was created, but saving failed: ${e.localizedMessage ?: "unknown error"}"
                } finally {
                    outputFile.delete()
                    exporting = false
                    exportButton.isEnabled = true
                    activeTransformer = null
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                outputFile.delete()
                exporting = false
                exportButton.isEnabled = true
                activeTransformer = null
                statusText.text = "Export failed: ${exportException.localizedMessage ?: "unsupported media or codec error"}. Try a different audio/photo file."
            }
        }

        try {
            activeTransformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            activeTransformer!!.start(Composition.Builder(videoSequence, audioSequence).build(), outputFile.absolutePath)
        } catch (e: Exception) {
            outputFile.delete()
            exporting = false
            exportButton.isEnabled = true
            activeTransformer = null
            statusText.text = "Could not start export: ${e.localizedMessage ?: "please try again"}"
        }
    }

    private fun copyToMovies(source: File): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "AI_Movie_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AI Movie Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the output video entry")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open the output video")
                val completed = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, completed, null, null)
                return uri
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        }

        val movies = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AI Movie Studio")
        if (!movies.exists() && !movies.mkdirs()) throw IllegalStateException("Could not create Movies folder")
        val destination = File(movies, "AI_Movie_${System.currentTimeMillis()}.mp4")
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(destination)
    }
}
