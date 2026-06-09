package com.example.hackofiesta.Database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicleLocationCountTable")
data class VehicleLocationData(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val currentState: String? = "No State",
    val currentCity: String? = "No City",
    val vehicleCount: Int? = 0
)
