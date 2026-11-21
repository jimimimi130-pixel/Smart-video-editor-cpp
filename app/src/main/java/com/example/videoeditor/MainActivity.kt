package com.example.videoeditor

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

data class EditorProject(
    val id: String,
    val title: String,
    val clips: Int,
    val durationSec: Int
)

class MainActivity : AppCompatActivity() {

    external fun prepareTimelineSummary(projectName: String, clipCount: Int, durationSec: Int): String

    private val projects = mutableListOf<EditorProject>()
    private lateinit var projectsListView: TextView
    private lateinit var coreStatusView: TextView
    private lateinit var advancedPanel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedProjects()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }

        val titleView = TextView(this).apply {
            text = "Smart Video Editor (Kotlin + C++)"
            textSize = 20f
        }

        val projectInput = EditText(this).apply {
            hint = "New project name (e.g. Dehok_Clip_01)"
        }

        val addButton = Button(this).apply {
            text = "Save New Project"
            setOnClickListener {
                val name = projectInput.text.toString().trim().ifEmpty {
                    "Project_${projects.size + 1}"
                }
                val newProj = EditorProject(
                    id = System.currentTimeMillis().toString(),
                    title = name,
                    clips = 1,
                    durationSec = 15
                )
                projects.add(newProj)
                saveProjectsToStorage()
                projectInput.text.clear()
                refreshUI(newProj)
            }
        }

        val toggleToolsButton = Button(this).apply {
            text = "More Tools"
            setOnClickListener {
                advancedPanel.visibility =
                    if (advancedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(16, 16, 16, 16)
        }

        val clearLastButton = Button(this).apply {
            text = "Delete Last Project"
            setOnClickListener {
                if (projects.isNotEmpty()) {
                    projects.removeAt(projects.lastIndex)
                    saveProjectsToStorage()
                    refreshUI(projects.lastOrNull())
                }
            }
        }
        advancedPanel.addView(clearLastButton)

        coreStatusView = TextView(this).apply {
            setPadding(0, 24, 0, 16)
            textSize = 15f
        }

        projectsListView = TextView(this).apply {
            textSize = 14f
        }

        val scrollView = ScrollView(this).apply {
            addView(projectsListView)
        }

        rootLayout.addView(titleView)
        rootLayout.addView(projectInput)
        rootLayout.addView(addButton)
        rootLayout.addView(toggleToolsButton)
        rootLayout.addView(advancedPanel)
        rootLayout.addView(coreStatusView)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
        refreshUI(projects.lastOrNull())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientationLabel = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "Landscape"
        } else {
            "Portrait"
        }
        coreStatusView.text = "${coreStatusView.text}\nOrientation: $orientationLabel"
    }

    private fun refreshUI(activeProject: EditorProject?) {
        coreStatusView.text = if (activeProject != null) {
            prepareTimelineSummary(activeProject.title, activeProject.clips, activeProject.durationSec)
        } else {
            prepareTimelineSummary("No Active Project", 0, 0)
        }

        projectsListView.text = if (projects.isEmpty()) {
            "Saved Projects: None"
        } else {
            "Saved Projects (${projects.size}):\n" + projects.joinToString("\n") {
                "- ${it.title} (${it.clips} clips, ${it.durationSec}s)"
            }
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
                    durationSec = obj.optInt("durationSec", 10)
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
