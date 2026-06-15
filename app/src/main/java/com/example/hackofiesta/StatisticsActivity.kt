package com.example.hackofiesta

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hackofiesta.Database.OverallDatabase
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatisticsActivity : AppCompatActivity() {

    private lateinit var database: OverallDatabase
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_statistics)

        database = OverallDatabase.getDatabase(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val barChart = findViewById<BarChart>(R.id.barChart)
        val pieChart = findViewById<PieChart>(R.id.pieChart)

        loadDataIntoChart(barChart)
        loadDataIntoPieChart(pieChart)

        findViewById<MaterialButton>(R.id.btnGenerateInsights).setOnClickListener {
            generateAiInsights()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadDataIntoChart(barChart: BarChart) {
        val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
        val selectedState = sharedPref.getString("selected_state", null)

        if (selectedState == null) return

        database.vehicleLocationDao().getVehicleDataByState(selectedState).observe(this) { data ->
            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()
            data.forEachIndexed { index, item ->
                entries.add(BarEntry(index.toFloat(), item.vehicleCount?.toFloat() ?: 0f))
                labels.add(item.currentCity ?: "Unknown")
            }

            val barDataSet = BarDataSet(entries, "Vehicle Counts")
            barDataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
            barDataSet.valueTextSize = 12f

            val barData = BarData(barDataSet)
            barData.barWidth = 0.7f

            barChart.data = barData
            barChart.description.isEnabled = false
            barChart.animateY(1200)
            
            val xAxis = barChart.xAxis
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)

            barChart.axisLeft.axisMinimum = 0f
            barChart.axisRight.isEnabled = false
            barChart.invalidate()
        }
    }

    private fun loadDataIntoPieChart(pieChart: PieChart) {
        val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
        val selectedState = sharedPref.getString("selected_state", null)

        if (selectedState == null) return

        database.vehicleLocationDao().getVehicleDataByState(selectedState).observe(this) { data ->
            val entries = ArrayList<PieEntry>()
            data.forEach { item ->
                entries.add(PieEntry(item.vehicleCount?.toFloat() ?: 0f, item.currentCity ?: "Unknown"))
            }

            val pieDataSet = PieDataSet(entries, "City Distribution")
            pieDataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
            pieDataSet.valueTextSize = 14f
            pieDataSet.valueTextColor = Color.WHITE

            val pieData = PieData(pieDataSet)
            pieChart.data = pieData
            pieChart.centerText = "Density"
            pieChart.description.isEnabled = false
            pieChart.animateXY(1000, 1000)
            pieChart.invalidate()
        }
    }

    private fun generateAiInsights() {
        val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
        val selectedState = sharedPref.getString("selected_state", "Unknown State") ?: "Unknown State"
        val tvAiInsights = findViewById<TextView>(R.id.tvAiInsights)

        tvAiInsights.text = getString(R.string.gathering_data_msg, selectedState)

        database.vehicleLocationDao().getVehicleDataByState(selectedState).observe(this) { dataList ->
            if (dataList.isNullOrEmpty()) {
                tvAiInsights.text = getString(R.string.insufficient_data_msg, selectedState)
                return@observe
            }

            val dataSummary = dataList.joinToString("\n") {
                "- City: ${it.currentCity}, Count: ${it.vehicleCount}"
            }

            val prompt = """
                URGENT TRAFFIC ANALYTICS REPORT: $selectedState
                
                CURRENT DATASET:
                $dataSummary
                
                TASK:
                Perform a location-based trend analysis. Identify potential congestion hotspots and suggest traffic management optimizations.
                
                STRICT FORMATTING CONSTRAINTS:
                1. DO NOT use markdown symbols (*, #).
                2. Use plain text only.
                3. Keep it concise (under 150 words).
                4. Use professional administrative tone.
            """.trimIndent()

            askGemini(prompt)
        }
    }

    private fun askGemini(prompt: String) {
        val tvAiInsights = findViewById<TextView>(R.id.tvAiInsights)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val json = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val responseJson = JSONObject(responseBody)
                        val rawAiText = responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        val cleanText = rawAiText
                            .replace(Regex("[*#]"), "")
                            .trim()

                        tvAiInsights.text = cleanText
                    } else {
                        tvAiInsights.text = getString(R.string.analysis_failed_msg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvAiInsights.text = getString(R.string.analysis_error_msg, e.localizedMessage)
                }
            }
        }
    }
}
