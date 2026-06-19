package com.example.hackofiesta

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hackofiesta.Database.OverallDatabase
import com.example.hackofiesta.Database.VehicleLocationData
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrontPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_front_page)

        val btn = findViewById<MaterialButton>(R.id.nextPageBtn)
        val scannerTile = findViewById<MaterialCardView>(R.id.scannerTile)
        val analyticsTile = findViewById<MaterialCardView>(R.id.analyticsTile)
        val aiInsightsTile = findViewById<MaterialCardView>(R.id.aiInsightsTile)
        val mapTile = findViewById<MaterialCardView>(R.id.mapTile)

        btn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        scannerTile.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        analyticsTile.setOnClickListener {
            showStateSelectionDialog()
        }

        aiInsightsTile.setOnClickListener {
            Toast.makeText(this, "AI Analysis requires a fresh scan.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
        }

        mapTile.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showStateSelectionDialog() {
        val database = OverallDatabase.getDatabase(this)
        val layoutOpen = layoutInflater.inflate(R.layout.state_card, null)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(layoutOpen)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val state = layoutOpen.findViewById<Spinner>(R.id.stateSpinner)
        val applyBtn = layoutOpen.findViewById<MaterialButton>(R.id.btnApply)

        lifecycleScope.launch(Dispatchers.IO) {
            val disStates = database.vehicleLocationDao().getDistinctStates()
            withContext(Dispatchers.Main) {
                if (disStates.isEmpty()) {
                    Toast.makeText(this@FrontPage, "No data available. Please perform a scan first.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@withContext
                }
                val stateAdapter = ArrayAdapter(
                    this@FrontPage,
                    android.R.layout.simple_spinner_item,
                    disStates
                )
                stateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                state.adapter = stateAdapter
                
                val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
                val savedState = sharedPref.getString("selected_state", null)
                if (savedState != null) {
                    val pos = disStates.indexOf(savedState)
                    if (pos >= 0) state.setSelection(pos)
                }
            }
        }

        applyBtn.setOnClickListener {
            val stateData = state.selectedItem?.toString()
            if (stateData != null) {
                val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE)
                sharedPref.edit().putString("selected_state", stateData).apply()
                startActivity(Intent(this, StatisticsActivity::class.java))
            }
            dialog.dismiss()
        }
    }
}