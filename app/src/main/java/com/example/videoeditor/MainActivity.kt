package com.example.videoeditor

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

data class EditorProject(
    val id: String,
    var title: String,
    var clips: Int,
    var durationSec: Int
)

class MainActivity : AppCompatActivity() {

    external fun prepareTimelineSummary(projectName: String, clipCount: Int, durationSec: Int): String

    private val projects = mutableListOf<EditorProject>()
    private var activeProjectIndex: Int = -1

    private lateinit var projectsContainer: LinearLayout
    private lateinit var coreStatusView: TextView
    private lateinit var activeProjectBadgeView: TextView
    private lateinit var timelineBarView: TextView
    private lateinit var orientationBadgeView: TextView
    private lateinit var advancedPanel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedProjects()
        if (projects.isNotEmpty() && activeProjectIndex !in projects.indices) {
            activeProjectIndex = projects.lastIndex
        }

        val bgDark = Color.parseColor("#0F1218")
        val cardDark = Color.parseColor("#181D27")
        val cardElevated = Color.parseColor("#202735")
        val accentBlue = Color.parseColor("#3B82F6")
        val accentTeal = Color.parseColor("#14B8A6")
        val dangerRed = Color.parseColor("#EF4444")
        val textPrimary = Color.parseColor("#F8FAFC")
        val textSecondary = Color.parseColor("#94A3B8")

        val mainScroll = ScrollView(this).apply {
            setBackgroundColor(bgDark)
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
            setPadding(28, 36, 28, 48)
        }

        // Top Header + Orientation Pill
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "Smart Video Editor"
            setTextColor(textPrimary)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        orientationBadgeView = TextView(this).apply {
            text = currentOrientationLabel()
            setTextColor(accentTeal)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.parseColor("#132A2E"), accentTeal)
            setPadding(20, 10, 20, 10)
        }

        headerRow.addView(titleView)
        headerRow.addView(orientationBadgeView)

        val subtitleView = TextView(this).apply {
            text = "Kotlin + C++ (NDK / CMake) • Studio Workspace"
            setTextColor(textSecondary)
            textSize = 13f
            setPadding(0, 6, 0, 20)
        }

        // Active Monitor & Timeline Card
        val monitorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(cardDark, Color.parseColor("#2B3446"))
            setPadding(24, 22, 24, 22)
        }

        activeProjectBadgeView = TextView(this).apply {
            text = "ACTIVE PROJECT"
            setTextColor(accentBlue)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        coreStatusView = TextView(this).apply {
            setTextColor(textPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, 8, 0, 12)
        }

        timelineBarView = TextView(this).apply {
            setTextColor(accentTeal)
            textSize = 12f
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            background = cardDrawable(cardElevated, Color.parseColor("#2E3A4E"))
            setPadding(16, 12, 16, 12)
        }

        monitorCard.addView(activeProjectBadgeView)
        monitorCard.addView(coreStatusView)
        monitorCard.addView(timelineBarView)

        // New Project Input Card
        val createCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(cardDark, Color.parseColor("#2B3446"))
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 18
            layoutParams = lp
        }

        val projectInput = EditText(this).apply {
            hint = "Project name (e.g. Duhok_Cut_01 or مشروع جديد)"
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
            textSize = 15f
            background = cardDrawable(cardElevated, Color.parseColor("#374151"))
            setPadding(20, 18, 20, 18)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 14
            layoutParams = lp
        }

        val addButton = Button(this).apply {
            text = "+ New Project"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = buttonDrawable(accentBlue)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 10
            layoutParams = lp
            setOnClickListener {
                val rawName = projectInput.text.toString().trim()
                val cleanName = if (rawName.isEmpty()) "Project_${projects.size + 1}" else rawName
                val newProj = EditorProject(
                    id = System.currentTimeMillis().toString(),
                    title = cleanName,
                    clips = 1,
                    durationSec = 15
                )
                projects.add(newProj)
                activeProjectIndex = projects.lastIndex
                saveProjectsToStorage()
                projectInput.text.clear()
                refreshUI()
            }
        }

        val toggleToolsButton = Button(this).apply {
            text = "More Tools"
            setTextColor(textPrimary)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = buttonDrawable(cardElevated)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = 10
            layoutParams = lp
            setOnClickListener {
                advancedPanel.visibility =
                    if (advancedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        buttonsRow.addView(addButton)
        buttonsRow.addView(toggleToolsButton)

        createCard.addView(projectInput)
        createCard.addView(buttonsRow)

        // More Tools Panel (Clip & Duration Controls + Delete)
        advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = cardDrawable(cardDark, accentBlue)
            setPadding(22, 20, 22, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 16
            layoutParams = lp
        }

        val toolsTitle = TextView(this).apply {
            text = "Active Project Controls"
            setTextColor(textPrimary)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 12)
        }

        val clipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addClipBtn = Button(this).apply {
            text = "+ Clip"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = buttonDrawable(accentTeal)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            layoutParams = lp
            setOnClickListener {
                val p = currentProject() ?: return@setOnClickListener
                p.clips += 1
                saveProjectsToStorage()
                refreshUI()
            }
        }

        val removeClipBtn = Button(this).apply {
            text = "- Clip"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = buttonDrawable(cardElevated)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = 8
            layoutParams = lp
            setOnClickListener {
                val p = currentProject() ?: return@setOnClickListener
                if (p.clips > 1) {
                    p.clips -= 1
                    saveProjectsToStorage()
                    refreshUI()
                }
            }
        }

        clipsRow.addView(addClipBtn)
        clipsRow.addView(removeClipBtn)

        val durationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 10
            layoutParams = lp
        }

        val addSecBtn = Button(this).apply {
            text = "+5s Duration"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = buttonDrawable(accentBlue)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            layoutParams = lp
            setOnClickListener {
                val p = currentProject() ?: return@setOnClickListener
                p.durationSec += 5
                saveProjectsToStorage()
                refreshUI()
            }
        }

        val subSecBtn = Button(this).apply {
            text = "-5s Duration"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = buttonDrawable(cardElevated)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = 8
            layoutParams = lp
            setOnClickListener {
                val p = currentProject() ?: return@setOnClickListener
                if (p.durationSec > 5) {
                    p.durationSec -= 5
                    saveProjectsToStorage()
                    refreshUI()
                }
            }
        }

        durationRow.addView(addSecBtn)
        durationRow.addView(subSecBtn)

        val deleteActiveBtn = Button(this).apply {
            text = "Delete Active Project"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = buttonDrawable(dangerRed)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp
            setOnClickListener {
                if (projects.isNotEmpty() && activeProjectIndex in projects.indices) {
                    projects.removeAt(activeProjectIndex)
                    activeProjectIndex = if (projects.isEmpty()) -1 else projects.lastIndex
                    saveProjectsToStorage()
                    refreshUI()
                }
            }
        }

        advancedPanel.addView(toolsTitle)
        advancedPanel.addView(clipsRow)
        advancedPanel.addView(durationRow)
        advancedPanel.addView(deleteActiveBtn)

        // Saved Projects Section
        val listHeader = TextView(this).apply {
            text = "Saved Projects (Tap to select)"
            setTextColor(textPrimary)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 24, 0, 12)
        }

        projectsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        rootLayout.addView(headerRow)
        rootLayout.addView(subtitleView)
        rootLayout.addView(monitorCard)
        rootLayout.addView(createCard)
        rootLayout.addView(advancedPanel)
        rootLayout.addView(listHeader)
        rootLayout.addView(projectsContainer)

        mainScroll.addView(rootLayout)
        setContentView(mainScroll)

        refreshUI()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        orientationBadgeView.text = currentOrientationLabel()
    }

    private fun currentOrientationLabel(): String {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLand) "LANDSCAPE" else "PORTRAIT"
    }

    private fun currentProject(): EditorProject? {
        return if (activeProjectIndex in projects.indices) projects[activeProjectIndex] else null
    }

    private fun refreshUI() {
        val active = currentProject()
        if (active != null) {
            coreStatusView.text = prepareTimelineSummary(active.title, active.clips, active.durationSec)
            val blocks = active.clips.coerceIn(1, 12)
            val bar = buildString {
                for (i in 1..blocks) {
                    append("[Clip #$i • ${active.durationSec / blocks}s] ")
                }
            }
            timelineBarView.text = "TIMELINE: $bar"
        } else {
            coreStatusView.text = prepareTimelineSummary("No Active Project", 0, 0)
            timelineBarView.text = "TIMELINE: Empty — create or select a project below"
        }

        projectsContainer.removeAllViews()
        if (projects.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No saved projects yet."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                background = cardDrawable(Color.parseColor("#181D27"), Color.parseColor("#2B3446"))
                setPadding(20, 18, 20, 18)
            }
            projectsContainer.addView(emptyView)
            return
        }

        projects.forEachIndexed { idx, project ->
            val isSelected = idx == activeProjectIndex
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cardDrawable(
                    if (isSelected) Color.parseColor("#1D2A44") else Color.parseColor("#181D27"),
                    if (isSelected) Color.parseColor("#3B82F6") else Color.parseColor("#2B3446")
                )
                setPadding(20, 18, 20, 18)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 12
                layoutParams = lp
                setOnClickListener {
                    activeProjectIndex = idx
                    refreshUI()
                }
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(this).apply {
                text = project.title
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }

            val metaTv = TextView(this).apply {
                text = "${project.clips} clips  •  ${project.durationSec}s"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                textDirection = View.TEXT_DIRECTION_LTR
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(0, 4, 0, 0)
            }

            infoCol.addView(nameTv)
            infoCol.addView(metaTv)

            val statusTv = TextView(this).apply {
                text = if (isSelected) "ACTIVE" else "SELECT"
                setTextColor(if (isSelected) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }

            itemCard.addView(infoCol)
            itemCard.addView(statusTv)
            projectsContainer.addView(itemCard)
        }
    }

    private fun cardDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 22f
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
    }

    private fun buttonDrawable(fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(fillColor)
        }
    }

    private fun pillDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
    }

    private fun loadSavedProjects() {
        val prefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("saved_projects_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        projects.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            projects.add(
                EditorProject(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    clips = obj.optInt("clips", 1),
                    durationSec = obj.optInt("durationSec", 15)
                )
            )
        }
    }

    private fun saveProjectsToStorage() {
        val arr = JSONArray()
        for (p in projects) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("clips", p.clips)
                put("durationSec", p.durationSec)
            }
            arr.put(obj)
        }
        val prefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("saved_projects_json", arr.toString()).apply()
    }

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
