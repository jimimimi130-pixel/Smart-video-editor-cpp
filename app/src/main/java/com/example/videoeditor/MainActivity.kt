package com.example.videoeditor

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class EditorProject(
    val id: String,
    var title: String,
    var clips: Int,
    var durationSec: Int,
    var filterName: String = "Natural",
    var speedFactor: Float = 1.0f,
    var volumePercent: Int = 100,
    var overlayText: String = "",
    var videoSource: String = "No Media",
    var videoUri: String = ""
)

class MainActivity : AppCompatActivity() {

    external fun prepareTimelineSummary(projectName: String, clipCount: Int, durationSec: Int): String

    private val projects = mutableListOf<EditorProject>()
    private var activeProjectIndex: Int = -1
    private var selectedClipIndex: Int = 0
    private var isPlaying: Boolean = false
    private var playheadSec: Int = 0
    private var toolsVisible: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            val p = currentProject()
            if (isPlaying && p != null) {
                val advance = if (p.speedFactor >= 1.5f) 2 else 1
                playheadSec += advance
                if (playheadSec > p.durationSec) {
                    playheadSec = 0
                    videoPreview.seekTo(0)
                }
                syncTimelineAndOverlay()
                handler.postDelayed(this, 1000L)
            } else {
                isPlaying = false
                updatePlayLabel()
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onVideoPicked(uri)
        }
    }

    // Studio Palette
    private val cBg = Color.parseColor("#0A0D12")
    private val cPanel = Color.parseColor("#131822")
    private val cPanelAlt = Color.parseColor("#1B2230")
    private val cBorder = Color.parseColor("#273247")
    private val cBlue = Color.parseColor("#38BDF8")
    private val cMint = Color.parseColor("#10B981")
    private val cGold = Color.parseColor("#F59E0B")
    private val cViolet = Color.parseColor("#A78BFA")
    private val cRose = Color.parseColor("#F43F5E")
    private val cWhite = Color.parseColor("#F8FAFC")
    private val cGray = Color.parseColor("#94A3B8")

    // Views
    private lateinit var orientationBadge: TextView
    private lateinit var videoPreview: VideoView
    private lateinit var filterTintLayer: View
    private lateinit var previewPlaceholderTv: TextView
    private lateinit var overlayTextTv: TextView
    private lateinit var activeProjectNameTv: TextView
    private lateinit var activeSpecsBadgeTv: TextView
    private lateinit var nativeEngineTv: TextView
    private lateinit var playToggleBtn: Button
    private lateinit var clockTv: TextView
    private lateinit var scrubber: SeekBar
    private lateinit var videoTrackBox: LinearLayout
    private lateinit var audioLayerTrackBox: LinearLayout
    private lateinit var projectInput: EditText
    private lateinit var overlayInput: EditText
    private lateinit var toolsDrawer: LinearLayout
    private lateinit var toggleToolsBtn: Button
    private lateinit var projectsListBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedProjects()
        if (projects.isNotEmpty() && activeProjectIndex !in projects.indices) {
            activeProjectIndex = projects.lastIndex
        }

        val outerScroll = ScrollView(this).apply {
            setBackgroundColor(cBg)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(20, 28, 20, 56)
        }

        root.addView(buildTopBar())
        root.addView(buildPreviewStage())
        root.addView(buildTransportCard())
        root.addView(buildMultiTrackTimeline())
        root.addView(buildProjectCreatorCard())
        root.addView(buildStudioToolbar())
        root.addView(buildProjectsSection())

        outerScroll.addView(root)
        setContentView(outerScroll)

        renderAll(reloadMedia = true)
    }

    private fun buildTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 14)

            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this@MainActivity).apply {
                text = "KINEMASTER // STUDIO LAB"
                setTextColor(cWhite)
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
            }

            val sub = TextView(this@MainActivity).apply {
                text = "Kotlin + C++ NDK • Multi-Track Editor"
                setTextColor(cGray)
                textSize = 12f
            }

            col.addView(title)
            col.addView(sub)

            orientationBadge = TextView(this@MainActivity).apply {
                text = orientationLabel()
                setTextColor(cBlue)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                background = pillBadge(Color.parseColor("#0B2231"), cBlue)
                setPadding(16, 7, 16, 7)
            }

            addView(col)
            addView(orientationBadge)
        }
    }

    private fun buildPreviewStage(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(cPanel, cBorder)
            setPadding(16, 14, 16, 14)

            val metaRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 10)
            }

            activeProjectNameTv = TextView(this@MainActivity).apply {
                text = "No Active Project"
                setTextColor(cWhite)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            activeSpecsBadgeTv = TextView(this@MainActivity).apply {
                text = "Natural • 1.0x • 100%"
                setTextColor(cMint)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                background = pillBadge(Color.parseColor("#0A281F"), cMint)
                setPadding(12, 5, 12, 5)
            }

            metaRow.addView(activeProjectNameTv)
            metaRow.addView(activeSpecsBadgeTv)

            val stageBox = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    420
                )
                background = panelBg(Color.parseColor("#06080C"), cBorder)
                clipToOutline = true
            }

            videoPreview = VideoView(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            }

            filterTintLayer = View(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.TRANSPARENT)
            }

            previewPlaceholderTv = TextView(this@MainActivity).apply {
                text = "Tap [Load Video] to preview media on stage"
                setTextColor(cGray)
                textSize = 13f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            overlayTextTv = TextView(this@MainActivity).apply {
                text = ""
                visibility = View.GONE
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                background = pillBadge(Color.parseColor("#AA000000"), cGold)
                setPadding(20, 8, 20, 8)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = 24
                }
            }

            stageBox.addView(videoPreview)
            stageBox.addView(filterTintLayer)
            stageBox.addView(previewPlaceholderTv)
            stageBox.addView(overlayTextTv)

            nativeEngineTv = TextView(this@MainActivity).apply {
                setTextColor(cBlue)
                textSize = 12f
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(2, 10, 2, 0)
            }

            addView(metaRow)
            addView(stageBox)
            addView(nativeEngineTv)
        }
    }

    private fun buildTransportCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(cPanel, cBorder)
            setPadding(16, 14, 16, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
            }

            playToggleBtn = Button(this@MainActivity).apply {
                text = "▶ Play"
                setTextColor(Color.parseColor("#051018"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = rectBtn(cBlue)
                setPadding(22, 4, 22, 4)
                setOnClickListener {
                    togglePlayback()
                }
            }

            val loadVideoBtn = Button(this@MainActivity).apply {
                text = "Load Video"
                setTextColor(cWhite)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = rectBtn(cPanelAlt)
                val lLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lLp.marginStart = 8
                layoutParams = lLp
                setOnClickListener {
                    ensureActiveProject()
                    pickVideoLauncher.launch("video/*")
                }
            }

            clockTv = TextView(this@MainActivity).apply {
                text = "00:00 / 00:00"
                setTextColor(cWhite)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(playToggleBtn)
            row.addView(loadVideoBtn)
            row.addView(clockTv)

            scrubber = SeekBar(this@MainActivity).apply {
                val sLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                sLp.topMargin = 8
                layoutParams = sLp
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            playheadSec = progress
                            if (videoPreview.duration > 0) {
                                videoPreview.seekTo(progress * 1000)
                            }
                            syncTimelineAndOverlay()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        pausePlayback()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            addView(row)
            addView(scrubber)
        }
    }

    private fun buildMultiTrackTimeline(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(cPanel, cBorder)
            setPadding(16, 14, 16, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp

            val head = TextView(this@MainActivity).apply {
                text = "MULTI-TRACK TIMELINE"
                setTextColor(cGold)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, 0, 0, 8)
            }

            val vLabel = TextView(this@MainActivity).apply {
                text = "V1 • VIDEO CLIPS"
                setTextColor(cGray)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, 0, 0, 6)
            }

            val vScroll = HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
            }

            videoTrackBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
            }
            vScroll.addView(videoTrackBox)

            val aLabel = TextView(this@MainActivity).apply {
                text = "A1 / FX • AUDIO & OVERLAY"
                setTextColor(cGray)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, 12, 0, 6)
            }

            audioLayerTrackBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
            }

            addView(head)
            addView(vLabel)
            addView(vScroll)
            addView(aLabel)
            addView(audioLayerTrackBox)
        }
    }

    private fun buildProjectCreatorCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(cPanel, cBorder)
            setPadding(16, 14, 16, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp

            projectInput = EditText(this@MainActivity).apply {
                hint = "اسم المشروع الجديد أو Duhok_Cut_01"
                setHintTextColor(cGray)
                setTextColor(cWhite)
                textSize = 14f
                background = panelBg(cPanelAlt, cBorder)
                setPadding(16, 13, 16, 13)
            }

            val btnRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                val bLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                bLp.topMargin = 10
                layoutParams = bLp
            }

            val createBtn = Button(this@MainActivity).apply {
                text = "+ New Project"
                setTextColor(Color.parseColor("#051018"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = rectBtn(cMint)
                val cLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                cLp.marginEnd = 6
                layoutParams = cLp
                setOnClickListener {
                    val raw = projectInput.text.toString().trim()
                    val title = if (raw.isEmpty()) "Project_${projects.size + 1}" else raw
                    projects.add(
                        EditorProject(
                            id = System.currentTimeMillis().toString(),
                            title = title,
                            clips = 2,
                            durationSec = 20,
                            filterName = "Natural",
                            speedFactor = 1.0f,
                            volumePercent = 100,
                            overlayText = "",
                            videoSource = "No Media",
                            videoUri = ""
                        )
                    )
                    activeProjectIndex = projects.lastIndex
                    selectedClipIndex = 0
                    playheadSec = 0
                    projectInput.text.clear()
                    closeKeyboard()
                    saveProjects()
                    renderAll(reloadMedia = true)
                }
            }

            toggleToolsBtn = Button(this@MainActivity).apply {
                text = if (toolsVisible) "Tools ▴" else "Tools ▾"
                setTextColor(cWhite)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = rectBtn(cPanelAlt)
                val tLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                tLp.marginStart = 6
                layoutParams = tLp
                setOnClickListener {
                    toolsVisible = !toolsVisible
                    toolsDrawer.visibility = if (toolsVisible) View.VISIBLE else View.GONE
                    toggleToolsBtn.text = if (toolsVisible) "Tools ▴" else "Tools ▾"
                }
            }

            btnRow.addView(createBtn)
            btnRow.addView(toggleToolsBtn)

            addView(projectInput)
            addView(btnRow)
        }
    }

    private fun buildStudioToolbar(): View {
        toolsDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (toolsVisible) View.VISIBLE else View.GONE
            background = panelBg(cPanel, cBlue)
            setPadding(16, 14, 16, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp

            val title = TextView(this@MainActivity).apply {
                text = "KINEMASTER-STYLE CONTEXT TOOLS"
                setTextColor(cBlue)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, 0, 0, 10)
            }

            // Row 1: Split / Add Clip / Trim Duration
            val r1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            r1.addView(toolBtn("✂ Split", cPanelAlt, cWhite) {
                mutateProject { p ->
                    p.clips = (p.clips + 1).coerceAtMost(24)
                    selectedClipIndex = (p.clips - 1).coerceAtLeast(0)
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 5
            })
            r1.addView(toolBtn("- Clip", cPanelAlt, cWhite) {
                mutateProject { p ->
                    p.clips = (p.clips - 1).coerceAtLeast(1)
                    if (selectedClipIndex >= p.clips) selectedClipIndex = p.clips - 1
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 5
                marginEnd = 5
            })
            r1.addView(toolBtn("+5s", cPanelAlt, cWhite) {
                mutateProject { p -> p.durationSec = (p.durationSec + 5).coerceAtMost(1800) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 5
            })

            // Row 2: Speed / Filter / Volume
            val r2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                val rLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rLp.topMargin = 8
                layoutParams = rLp
            }
            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
            r2.addView(toolBtn("⚡ Speed", Color.parseColor("#221936"), cViolet) {
                mutateProject { p ->
                    val cur = speeds.indexOfFirst { kotlin.math.abs(it - p.speedFactor) < 0.05f }
                    p.speedFactor = speeds[if (cur == -1) 1 else (cur + 1) % speeds.size]
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 5
            })
            val filters = listOf("Natural", "Cinema", "Vivid", "Noir", "Warm")
            r2.addView(toolBtn("🎨 Filter", Color.parseColor("#0D2820"), cMint) {
                mutateProject { p ->
                    val next = (filters.indexOf(p.filterName) + 1) % filters.size
                    p.filterName = filters[next]
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 5
                marginEnd = 5
            })
            r2.addView(toolBtn("🔊 Vol", Color.parseColor("#2A200E"), cGold) {
                mutateProject { p ->
                    p.volumePercent = when {
                        p.volumePercent >= 100 -> 50
                        p.volumePercent >= 50 -> 0
                        else -> 100
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 5
            })

            // Row 3: Text Overlay Input + Apply
            val overlayRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
                val oLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                oLp.topMargin = 10
                layoutParams = oLp
            }

            overlayInput = EditText(this@MainActivity).apply {
                hint = "نص طبقة على الفيديو..."
                setHintTextColor(cGray)
                setTextColor(cWhite)
                textSize = 13f
                background = panelBg(cPanelAlt, cBorder)
                setPadding(14, 11, 14, 11)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                }
            }

            val applyTextBtn = toolBtn("T+ Layer", cBlue, Color.parseColor("#051018")) {
                val txt = overlayInput.text.toString().trim()
                mutateProject { p -> p.overlayText = txt }
                closeKeyboard()
            }

            overlayRow.addView(overlayInput)
            overlayRow.addView(applyTextBtn)

            // Delete Active Project
            val deleteBtn = toolBtn("Delete Active Project", Color.parseColor("#2B1018"), cRose) {
                if (projects.isNotEmpty() && activeProjectIndex in projects.indices) {
                    pausePlayback()
                    projects.removeAt(activeProjectIndex)
                    activeProjectIndex = if (projects.isEmpty()) -1 else projects.lastIndex
                    selectedClipIndex = 0
                    playheadSec = 0
                    saveProjects()
                    renderAll(reloadMedia = true)
                }
            }.apply {
                val dLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                dLp.topMargin = 10
                layoutParams = dLp
            }

            addView(title)
            addView(r1)
            addView(r2)
            addView(overlayRow)
            addView(deleteBtn)
        }
        return toolsDrawer
    }

    private fun buildProjectsSection(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 18
            layoutParams = lp

            val lbl = TextView(this@MainActivity).apply {
                text = "SAVED PROJECTS"
                setTextColor(cWhite)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(4, 0, 4, 8)
            }

            projectsListBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            addView(lbl)
            addView(projectsListBox)
        }
    }

    private fun ensureActiveProject() {
        if (currentProject() == null) {
            val title = projectInput.text.toString().trim().ifEmpty { "Studio_Cut_${projects.size + 1}" }
            projects.add(
                EditorProject(
                    id = System.currentTimeMillis().toString(),
                    title = title,
                    clips = 2,
                    durationSec = 20,
                    filterName = "Natural",
                    speedFactor = 1.0f,
                    volumePercent = 100,
                    overlayText = "",
                    videoSource = "No Media",
                    videoUri = ""
                )
            )
            activeProjectIndex = projects.lastIndex
            selectedClipIndex = 0
            projectInput.text.clear()
            saveProjects()
            renderAll(reloadMedia = false)
        }
    }

    private fun onVideoPicked(uri: Uri) {
        val p = currentProject() ?: return
        var sec = p.durationSec
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Picked_Video"

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (ms > 0L) {
                sec = ((ms + 500L) / 1000L).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (name.length > 26) {
            name = name.take(23) + "..."
        }

        p.videoSource = name
        p.videoUri = uri.toString()
        p.durationSec = sec
        playheadSec = 0
        saveProjects()
        renderAll(reloadMedia = true)
    }

    private fun mutateProject(block: (EditorProject) -> Unit) {
        val p = currentProject() ?: return
        block(p)
        saveProjects()
        renderAll(reloadMedia = false)
    }

    private fun currentProject(): EditorProject? {
        return if (activeProjectIndex in projects.indices) projects[activeProjectIndex] else null
    }

    private fun renderAll(reloadMedia: Boolean) {
        val p = currentProject()
        if (p != null) {
            activeProjectNameTv.text = p.title
            activeSpecsBadgeTv.text =
                "${p.filterName} • ${String.format(Locale.US, "%.1fx", p.speedFactor)} • ${p.volumePercent}%"
            nativeEngineTv.text = prepareTimelineSummary(p.title, p.clips, p.durationSec)
            scrubber.max = p.durationSec.coerceAtLeast(1)
            if (playheadSec > p.durationSec) playheadSec = 0
            scrubber.progress = playheadSec
            if (overlayInput.text.isEmpty() && p.overlayText.isNotEmpty()) {
                overlayInput.setText(p.overlayText)
            }
            applyFilterTint(p.filterName)
            if (reloadMedia) {
                bindVideoSource(p)
            }
        } else {
            activeProjectNameTv.text = "No Active Project"
            activeSpecsBadgeTv.text = "Natural • 1.0x • 100%"
            nativeEngineTv.text = prepareTimelineSummary("No Active Project", 0, 0)
            playheadSec = 0
            scrubber.max = 1
            scrubber.progress = 0
            applyFilterTint("Natural")
            if (reloadMedia) {
                videoPreview.stopPlayback()
                previewPlaceholderTv.visibility = View.VISIBLE
            }
        }

        updatePlayLabel()
        syncTimelineAndOverlay()
        renderProjectsList()
    }

    private fun bindVideoSource(p: EditorProject) {
        if (p.videoUri.isNotBlank()) {
            try {
                val uri = Uri.parse(p.videoUri)
                previewPlaceholderTv.visibility = View.GONE
                videoPreview.setVideoURI(uri)
                videoPreview.seekTo(playheadSec * 1000)
                if (isPlaying) {
                    videoPreview.start()
                }
                return
            } catch (_: Exception) {
            }
        }
        videoPreview.stopPlayback()
        previewPlaceholderTv.text = "Media: ${p.videoSource} • Tap [Load Video]"
        previewPlaceholderTv.visibility = View.VISIBLE
    }

    private fun applyFilterTint(filter: String) {
        val color = when (filter) {
            "Cinema" -> Color.parseColor("#240B3855")
            "Vivid" -> Color.parseColor("#2400B4D8")
            "Noir" -> Color.parseColor("#66111111")
            "Warm" -> Color.parseColor("#26F59E0B")
            else -> Color.TRANSPARENT
        }
        filterTintLayer.setBackgroundColor(color)
    }

    private fun syncTimelineAndOverlay() {
        val p = currentProject()
        clockTv.text = "${formatTime(playheadSec)} / ${formatTime(p?.durationSec ?: 0)}"
        scrubber.progress = playheadSec

        if (p != null && p.overlayText.isNotBlank()) {
            overlayTextTv.text = p.overlayText
            overlayTextTv.visibility = View.VISIBLE
        } else {
            overlayTextTv.text = ""
            overlayTextTv.visibility = View.GONE
        }

        videoTrackBox.removeAllViews()
        audioLayerTrackBox.removeAllViews()

        if (p == null) {
            val empty = TextView(this).apply {
                text = "Create a project or load a video to see tracks."
                setTextColor(cGray)
                textSize = 12f
            }
            videoTrackBox.addView(empty)
            return
        }

        val count = p.clips.coerceAtLeast(1)
        val segSec = p.durationSec.toFloat() / count.toFloat()

        for (i in 0 until count) {
            val startSec = (i * segSec).toInt()
            val endSec = ((i + 1) * segSec).toInt()
            val onPlayhead = if (i == count - 1) playheadSec >= startSec else playheadSec in startSec until endSec
            val isSelected = i == selectedClipIndex

            val clipCard = TextView(this).apply {
                text = "Clip ${i + 1} (${String.format(Locale.US, "%.1fs", segSec)})"
                setTextColor(if (onPlayhead) Color.parseColor("#051018") else cWhite)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = pillBadge(
                    if (onPlayhead) cBlue else cPanelAlt,
                    if (isSelected) cGold else cBorder
                )
                setPadding(20, 14, 20, 14)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 8
                layoutParams = lp
                setOnClickListener {
                    selectedClipIndex = i
                    playheadSec = startSec.coerceIn(0, p.durationSec)
                    if (videoPreview.duration > 0) {
                        videoPreview.seekTo(playheadSec * 1000)
                    }
                    syncTimelineAndOverlay()
                }
            }
            videoTrackBox.addView(clipCard)
        }

        val audioPill = TextView(this).apply {
            text = "♪ Audio ${p.volumePercent}%"
            setTextColor(cGold)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = pillBadge(Color.parseColor("#261E0E"), cGold)
            setPadding(16, 10, 16, 10)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 8
            layoutParams = lp
        }

        val layerPill = TextView(this).apply {
            val label = if (p.overlayText.isBlank()) "T Layer: None" else "T Layer: ${p.overlayText}"
            text = label
            setTextColor(cViolet)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = pillBadge(Color.parseColor("#211834"), cViolet)
            setPadding(16, 10, 16, 10)
        }

        audioLayerTrackBox.addView(audioPill)
        audioLayerTrackBox.addView(layerPill)
    }

    private fun renderProjectsList() {
        projectsListBox.removeAllViews()
        if (projects.isEmpty()) {
            val empty = TextView(this).apply {
                text = "لا توجد مشاريع محفوظة بعد — أضف مشروعاً جديداً للبدء."
                setTextColor(cGray)
                textSize = 13f
                background = panelBg(cPanel, cBorder)
                setPadding(16, 14, 16, 14)
            }
            projectsListBox.addView(empty)
            return
        }

        projects.forEachIndexed { idx, proj ->
            val active = idx == activeProjectIndex
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
                background = panelBg(
                    if (active) Color.parseColor("#0F2135") else cPanel,
                    if (active) cBlue else cBorder
                )
                setPadding(16, 14, 16, 14)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 8
                layoutParams = lp
                setOnClickListener {
                    pausePlayback()
                    activeProjectIndex = idx
                    selectedClipIndex = 0
                    playheadSec = 0
                    overlayInput.setText(proj.overlayText)
                    renderAll(reloadMedia = true)
                }
            }

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nTv = TextView(this).apply {
                text = proj.title
                setTextColor(cWhite)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }

            val dTv = TextView(this).apply {
                text = "${proj.clips} clips • ${proj.durationSec}s • ${proj.filterName} • ${proj.videoSource}"
                setTextColor(cGray)
                textSize = 12f
                textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, 3, 0, 0)
            }

            left.addView(nTv)
            left.addView(dTv)

            val badge = TextView(this).apply {
                text = if (active) "ACTIVE" else "OPEN"
                setTextColor(if (active) cBlue else cGray)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(8, 0, 0, 0)
            }

            card.addView(left)
            card.addView(badge)
            projectsListBox.addView(card)
        }
    }

    private fun togglePlayback() {
        if (currentProject() == null) return
        if (isPlaying) {
            pausePlayback()
        } else {
            isPlaying = true
            updatePlayLabel()
            if (videoPreview.duration > 0) {
                videoPreview.seekTo(playheadSec * 1000)
                videoPreview.start()
            }
            handler.removeCallbacks(tickerRunnable)
            handler.postDelayed(tickerRunnable, 1000L)
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlayLabel()
        handler.removeCallbacks(tickerRunnable)
        try {
            if (videoPreview.isPlaying) {
                videoPreview.pause()
            }
        } catch (_: Exception) {
        }
    }

    private fun updatePlayLabel() {
        playToggleBtn.text = if (isPlaying) "❚❚ Pause" else "▶ Play"
    }

    private fun toolBtn(label: String, bgColor: Int, fgColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(fgColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            textDirection = View.TEXT_DIRECTION_LTR
            background = rectBtn(bgColor)
            setOnClickListener { onClick() }
        }
    }

    private fun closeKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
        projectInput.clearFocus()
        overlayInput.clearFocus()
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun orientationLabel(): String {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLand) "LANDSCAPE" else "PORTRAIT"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        orientationBadge.text = orientationLabel()
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickerRunnable)
    }

    private fun panelBg(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 20f
        setColor(fill)
        setStroke(2, stroke)
    }

    private fun rectBtn(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 15f
        setColor(fill)
    }

    private fun pillBadge(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 50f
        setColor(fill)
        setStroke(2, stroke)
    }

    private fun loadSavedProjects() {
        val prefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("saved_projects_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        projects.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            projects.add(
                EditorProject(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    clips = o.optInt("clips", 2),
                    durationSec = o.optInt("durationSec", 20),
                    filterName = o.optString("filterName", "Natural"),
                    speedFactor = o.optDouble("speedFactor", 1.0).toFloat(),
                    volumePercent = o.optInt("volumePercent", 100),
                    overlayText = o.optString("overlayText", ""),
                    videoSource = o.optString("videoSource", "No Media"),
                    videoUri = o.optString("videoUri", "")
                )
            )
        }
    }

    private fun saveProjects() {
        val arr = JSONArray()
        for (p in projects) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("clips", p.clips)
                put("durationSec", p.durationSec)
                put("filterName", p.filterName)
                put("speedFactor", p.speedFactor.toDouble())
                put("volumePercent", p.volumePercent)
                put("overlayText", p.overlayText)
                put("videoSource", p.videoSource)
                put("videoUri", p.videoUri)
            })
        }
        getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("saved_projects_json", arr.toString())
            .apply()
    }

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
