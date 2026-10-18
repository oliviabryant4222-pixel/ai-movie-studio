package com.aimoviestudio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var mediaText: TextView
    private lateinit var root: LinearLayout

    private var audioUri: Uri? = null
    private val mediaUris = mutableListOf<Uri>()

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                audioUri = uri
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                updateMediaInfo()
                statusText.text = "Voiceover audio selected successfully."
            }
        }

    private val mediaPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                mediaUris.clear()
                mediaUris.addAll(uris)
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                }
                updateMediaInfo()
                statusText.text = "${uris.size} media file(s) selected."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(15, 18, 30))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "AI MOVIE STUDIO"
            textSize = 28f
            setTextColor(Color.rgb(100, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Your mobile video editing workspace"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(subtitle)

        addSection("1. VOICEOVER")
        addButton("Choose voiceover audio") {
            audioPicker.launch(arrayOf("audio/*"))
        }

        addSection("2. VISUAL MEDIA")
        addButton("Choose images or videos") {
            mediaPicker.launch(arrayOf("image/*", "video/*"))
        }

        addSection("3. PROJECT FILES")
        mediaText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            text = "No files selected yet."
        }
        root.addView(mediaText)

        addSection("4. WORKSPACE STATUS")
        statusText = TextView(this).apply {
            text = "Ready. Select an audio file and visual media to begin."
            textSize = 14f
            setTextColor(Color.rgb(120, 240, 170))
            setPadding(12, 12, 12, 16)
        }
        root.addView(statusText)

        addButton("Clear selected files") {
            audioUri = null
            mediaUris.clear()
            updateMediaInfo()
            statusText.text = "Selection cleared."
        }

        val note = TextView(this).apply {
            text = "\nCurrent version: media selection foundation.\nAI narration matching, timeline editing, effects and video export still need to be built."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(8, 20, 8, 20)
        }
        root.addView(note)

        setContentView(scroll)
    }

    private fun addSection(text: String) {
        val heading = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.rgb(100, 220, 255))
            setPadding(0, 22, 0, 8)
        }
        root.addView(heading)
    }

    private fun addButton(label: String, action: () -> Unit) {
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
            ).apply {
                bottomMargin = 8
            }
        )
    }

    private fun updateMediaInfo() {
        val lines = mutableListOf<String>()

        lines.add(
            if (audioUri != null) {
                "Voiceover: ${audioUri!!.lastPathSegment ?: "Audio selected"}"
            } else {
                "Voiceover: Not selected"
            }
        )

        lines.add("")
        lines.add("Visual files: ${mediaUris.size}")

        mediaUris.forEachIndexed { index, uri ->
            lines.add("${index + 1}. ${uri.lastPathSegment ?: "Media file"}")
        }

        mediaText.text = lines.joinToString("\n")
    }
}
