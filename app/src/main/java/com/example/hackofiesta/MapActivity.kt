package com.example.hackofiesta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hackofiesta.Database.OverallDatabase
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var database: OverallDatabase
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        database = OverallDatabase.getDatabase(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.mapToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        loadPreferences()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.map)) { v, insets ->
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

        findViewById<MaterialButton>(R.id.btnTrafficLayer).setOnClickListener {
            mMap.isTrafficEnabled = !mMap.isTrafficEnabled
            (it as MaterialButton).isActivated = mMap.isTrafficEnabled
            savePreference("traffic_enabled", mMap.isTrafficEnabled)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabResetView).setOnClickListener {
            loadHotspots(recenter = true)
        }
    }

    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("map_prefs", MODE_PRIVATE)
        // Preferences are applied in onMapReady when mMap is initialized
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
        
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isMapToolbarEnabled = true

        val sharedPref = getSharedPreferences("map_prefs", MODE_PRIVATE)
        mMap.mapType = sharedPref.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL)
        mMap.isTrafficEnabled = sharedPref.getBoolean("traffic_enabled", false)
        
        // Update UI state for toggles
        if (mMap.mapType == GoogleMap.MAP_TYPE_SATELLITE) {
            findViewById<MaterialButtonToggleGroup>(R.id.mapTypeToggle).check(R.id.btnSatellite)
        } else {
            findViewById<MaterialButtonToggleGroup>(R.id.mapTypeToggle).check(R.id.btnNormal)
        }
        findViewById<MaterialButton>(R.id.btnTrafficLayer).isActivated = mMap.isTrafficEnabled

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
        val btnViewStats = findViewById<MaterialButton>(R.id.btnViewStats)

        val titleParts = marker.title?.split(", ")
        val city = titleParts?.getOrNull(0) ?: "Unknown City"
        val state = titleParts?.getOrNull(1) ?: "Unknown State"
        
        cityNameView.text = city
        stateNameView.text = state
        vehicleCountView.text = marker.snippet

        btnViewStats.setOnClickListener {
            val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
            sharedPref.edit().putString("selected_state", state).apply()

            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

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
                mMap.clear()
                var firstLocation: LatLng? = null
                for (data in filtered) {
                    if (data.latitude != null && data.longitude != null && (data.latitude != 0.0 || data.longitude != 0.0)) {
                        val pos = LatLng(data.latitude, data.longitude)
                        if (firstLocation == null) firstLocation = pos
                        
                        val markerColor = when {
                            (data.vehicleCount ?: 0) > 20 -> BitmapDescriptorFactory.HUE_RED
                            (data.vehicleCount ?: 0) > 10 -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_GREEN
                        }

                        mMap.addMarker(
                            MarkerOptions()
                                .position(pos)
                                .title("${data.currentCity}, ${data.currentState}")
                                .snippet("Vehicles: ${data.vehicleCount}")
                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                        )
                    }
                }
                firstLocation?.let {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
                }
            } else {
                Toast.makeText(this, "No hotspots found for '$query'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHotspots(recenter: Boolean = false) {
        // Since getVehicleLocationAll() returns LiveData, we don't need to be in IO dispatcher to observe it
        // but we need to be on Main to observe.
        runOnUiThread {
            database.vehicleLocationDao().getVehicleLocationAll().observe(this@MapActivity) { dataList ->
                if (dataList != null) {
                    mMap.clear()
                    var firstLocation: LatLng? = null

                    for (data in dataList) {
                        if (data.latitude != null && data.longitude != null && (data.latitude != 0.0 || data.longitude != 0.0)) {
                            val pos = LatLng(data.latitude, data.longitude)
                            if (firstLocation == null) firstLocation = pos

                            val markerColor = when {
                                (data.vehicleCount ?: 0) > 20 -> BitmapDescriptorFactory.HUE_RED
                                (data.vehicleCount ?: 0) > 10 -> BitmapDescriptorFactory.HUE_ORANGE
                                else -> BitmapDescriptorFactory.HUE_GREEN
                            }

                            mMap.addMarker(
                                MarkerOptions()
                                    .position(pos)
                                    .title("${data.currentCity}, ${data.currentState}")
                                    .snippet("Vehicles: ${data.vehicleCount}")
                                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                            )
                        }
                    }

                    firstLocation?.let {
                        if (recenter || dataList.size == 1) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
                        }
                    }
                }
            }
        }
    }
}