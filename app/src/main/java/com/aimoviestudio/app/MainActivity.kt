package com.aimoviestudio.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 18, 24)
    private val panel = Color.rgb(28, 32, 42)
    private val accent = Color.rgb(90, 160, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(185, 192, 205)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 32, 22, 28)
            background = null
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("AI MOVIE STUDIO", 27f, white, true))
        root.addView(text("ULTIMATE • Android build foundation", 13f, accent, true))
        root.addView(spacer(22))

        val status = text(
            "PROJECT FOUNDATION\nThis is the initial app shell. AI matching, timeline editing, audio tools and rendering are not implemented yet.",
            14f, muted, false
        )
        status.setPadding(16, 16, 16, 16)
        status.setBackgroundColor(panel)
        root.addView(status)
        root.addView(spacer(20))

        root.addView(section("1  •  MEDIA INPUT"))
        root.addView(actionButton("Choose voiceover", "Audio picker will be added in the next development step."))
        root.addView(actionButton("Choose images / video", "Media picker and thumbnail analysis will be added next."))
        root.addView(spacer(14))

        root.addView(section("2  •  AI WORKFLOW"))
        root.addView(actionButton("Analyze narration", "Speech recognition and timestamp extraction are not connected yet."))
        root.addView(actionButton("Match visuals to narration", "Semantic matching engine is not connected yet."))
        root.addView(actionButton("Build edit timeline", "Timeline engine is planned; it is not implemented yet."))
        root.addView(spacer(14))

        root.addView(section("3  •  EDIT & EXPORT"))
        root.addView(actionButton("Open editor", "Multi-track timeline and effects are planned for later builds."))
        root.addView(actionButton("Export video", "Rendering pipeline is not implemented yet."))
        root.addView(spacer(22))

        root.addView(text("ZERO-BUDGET DESIGN", 13f, accent, true))
        root.addView(text(
            "The goal is to avoid mandatory paid APIs. Free cloud AI may have usage limits; local processing will be used where practical.",
            13f, muted, false
        ))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 3, 0, 3)
        }

    private fun section(value: String): TextView =
        text(value, 16f, white, true).apply { setPadding(0, 4, 0, 10) }

    private fun spacer(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun actionButton(label: String, message: String): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(panel)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle(label)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
}
