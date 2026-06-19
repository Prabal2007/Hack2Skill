package com.example.hackofiesta

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hackofiesta.Database.OverallDatabase
import com.example.hackofiesta.Database.VehicleLocationData
import com.example.hackofiesta.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var database: OverallDatabase
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private var currentHotspots: List<VehicleLocationData> = emptyList()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        database = OverallDatabase.getDatabase(this)

        val backBtn = findViewById<MaterialButton>(R.id.backBtn)

        backBtn.setOnClickListener {
            finish()
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.mapToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapXml)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        val bottomSheet = findViewById<MaterialCardView>(R.id.detailsBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        val etSearch = findViewById<EditText>(R.id.etSearchHotspots)
        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchHotspots(v.text.toString())
                true
            } else {
                false
            }
        }

        findViewById<MaterialButton>(R.id.btnAiAnalysis).setOnClickListener {
            generateAiSummaryReport()
        }

        val mapTypeToggle = findViewById<MaterialButtonToggleGroup>(R.id.mapTypeToggle)
        mapTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val type = when (checkedId) {
                    R.id.btnNormal -> GoogleMap.MAP_TYPE_NORMAL
                    R.id.btnSatellite -> GoogleMap.MAP_TYPE_SATELLITE
                    else -> GoogleMap.MAP_TYPE_NORMAL
                }
                mMap.mapType = type
                savePreference("map_type", type)
            }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabResetView).setOnClickListener {
            loadHotspots(recenter = true)
        }
    }

    private fun savePreference(key: String, value: Any) {
        val sharedPref = getSharedPreferences("map_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.isTrafficEnabled = true

        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isMapToolbarEnabled = true

        val sharedPref = getSharedPreferences("map_prefs", MODE_PRIVATE)
        mMap.mapType = sharedPref.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL)
        
        if (mMap.mapType == GoogleMap.MAP_TYPE_SATELLITE) {
            findViewById<MaterialButtonToggleGroup>(R.id.mapTypeToggle).check(R.id.btnSatellite)
        } else {
            findViewById<MaterialButtonToggleGroup>(R.id.mapTypeToggle).check(R.id.btnNormal)
        }

        mMap.setOnMarkerClickListener { marker ->
            showMarkerDetails(marker)
            false
        }

        mMap.setOnMapClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        
        loadHotspots()
    }

    private fun showMarkerDetails(marker: Marker) {
        val cityNameView = findViewById<TextView>(R.id.sheetCityName)
        val stateNameView = findViewById<TextView>(R.id.sheetStateName)
        val vehicleCountView = findViewById<TextView>(R.id.sheetVehicleCount)
        val aiAnalysisView = findViewById<TextView>(R.id.sheetAiAnalysis)

        val titleParts = marker.title?.split(", ")
        val city = titleParts?.getOrNull(0) ?: "Unknown City"
        val state = titleParts?.getOrNull(1) ?: "Unknown State"
        
        cityNameView.text = city
        stateNameView.text = state
        vehicleCountView.text = marker.snippet

        aiAnalysisView.visibility = View.VISIBLE
        aiAnalysisView.text = "Fetching Gemini Hotspot Analysis..."
        
        askGeminiForMarker(city, state, marker.snippet ?: "", aiAnalysisView)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun searchHotspots(query: String) {
        if (query.isBlank()) {
            loadHotspots()
            return
        }

        database.vehicleLocationDao().getVehicleLocationAll().observe(this) { dataList ->
            val filtered = dataList.filter {
                it.currentCity?.contains(query, ignoreCase = true) == true ||
                it.currentState?.contains(query, ignoreCase = true) == true
            }
            if (filtered.isNotEmpty()) {
                currentHotspots = filtered
                updateMapMarkers(filtered, recenter = true)
            } else {
                Toast.makeText(this, "No hotspots found for '$query'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHotspots(recenter: Boolean = false) {
        database.vehicleLocationDao().getVehicleLocationAll().observe(this) { dataList ->
            if (dataList != null) {
                currentHotspots = dataList
                updateMapMarkers(dataList, recenter)
            }
        }
    }

    private fun updateMapMarkers(dataList: List<VehicleLocationData>, recenter: Boolean) {
        mMap.clear()
        var firstLocation: LatLng? = null

        for (data in dataList) {
            val lat = data.latitude ?: 0.0
            val lon = data.longitude ?: 0.0
            if (lat != 0.0 || lon != 0.0) {
                val pos = LatLng(lat, lon)
                if (firstLocation == null) firstLocation = pos

                val vehicleCount = data.vehicleCount ?: 0
                val plateCount = data.plateCount ?: 0

                val isCriticalHotspot = vehicleCount > 25 && plateCount > 5
                
                val (color, markerHue) = when {
                    isCriticalHotspot -> Color.argb(160, 255, 0, 0) to BitmapDescriptorFactory.HUE_RED
                    vehicleCount > 20 && plateCount > 4 -> Color.argb(100, 255, 0, 0) to BitmapDescriptorFactory.HUE_RED
                    vehicleCount >= 15 -> Color.argb(100, 255, 165, 0) to BitmapDescriptorFactory.HUE_ORANGE
                    else -> Color.argb(100, 0, 255, 0) to BitmapDescriptorFactory.HUE_GREEN
                }

                val radius = if (isCriticalHotspot) 800.0 else 500.0
                
                mMap.addCircle(CircleOptions()
                    .center(pos)
                    .radius(radius)
                    .fillColor(color)
                    .strokeColor(if (isCriticalHotspot) Color.RED else Color.TRANSPARENT)
                    .strokeWidth(if (isCriticalHotspot) 5f else 0f))

                mMap.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("${data.currentCity}, ${data.currentState}")
                        .snippet("Vehicles: $vehicleCount | Plates: $plateCount")
                        .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
                )
            }
        }

        if (dataList.isNotEmpty() && (recenter || dataList.size == 1)) {
            val boundsBuilder = LatLngBounds.Builder()
            var validPoints = 0

            for (data in dataList) {
                val lat = data.latitude ?: 0.0
                val lon = data.longitude ?: 0.0
                if (lat != 0.0 || lon != 0.0) {
                    boundsBuilder.include(LatLng(lat, lon))
                    validPoints++
                }
            }

            if (validPoints > 0) {
                val bounds = boundsBuilder.build()
                val padding = 180 // Safe pixel offset cushion from screen walls
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            }
        }
    }

    private fun generateAiSummaryReport() {
        if (currentHotspots.isEmpty()) {
            Toast.makeText(this, "No hotspot data to analyze.", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar = findViewById<ProgressBar>(R.id.aiProgressBar)
        val aiBtn = findViewById<MaterialButton>(R.id.btnAiAnalysis)

        progressBar.visibility = View.VISIBLE
        aiBtn.visibility = View.GONE

        val hotspotData = currentHotspots.joinToString("\n") {
            "- ${it.currentCity} (${it.currentState}): ${it.vehicleCount} vehicles, ${it.plateCount} plates detected."
        }

        val prompt = """
            OFFICIAL HOTSPOT ANALYSIS REPORT
            Analyze the following traffic violation hotspots based on vehicle density and plate detection counts.
            Identify the highest risk zones (Hotspots) where both vehicle count and plate detections are elevated.
            Provide 3 key recommendations for law enforcement deployment at these specific hotspots.
            
            HOTSPOT DATA:
            $hotspotData
            
            Format: Formal administrative summary, No markdown (* or #), Plain text only.
        """.trimIndent()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = askGemini(prompt)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                aiBtn.visibility = View.VISIBLE
                if (result != null) {
                    val cleanText = result.replace(Regex("[*#]"), "").trim()
                    
                    val aiAnalysisView = findViewById<TextView>(R.id.sheetAiAnalysis)
                    findViewById<TextView>(R.id.sheetCityName).text = "AI HOTSPOT REPORT"
                    findViewById<TextView>(R.id.sheetStateName).text = "Global Summary"
                    findViewById<TextView>(R.id.sheetVehicleCount).text = "Analyzed ${currentHotspots.size} Hotspots"
                    
                    aiAnalysisView.visibility = View.VISIBLE
                    aiAnalysisView.text = cleanText
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    private fun askGeminiForMarker(city: String, state: String, stats: String, targetView: TextView) {
        val prompt = """
            Analyze this specific traffic hotspot: $city, $state.
            Stats: $stats.
            Considering both the vehicle count and number plate detections, why is this a priority area? 
            Provide a formal 1-sentence risk assessment highlighting if it's a "Hotspot" for enforcement.
            No markdown, plain text only.
        """.trimIndent()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = askGemini(prompt)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    targetView.text = result.replace(Regex("[*#]"), "").trim()
                } else {
                    targetView.text = "AI insight unavailable."
                }
            }
        }
    }

    private suspend fun askGemini(prompt: String): String? {
        return withContext(Dispatchers.IO) {
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
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${apiKey}")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val responseJson = JSONObject(responseBody)
                    responseJson.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
