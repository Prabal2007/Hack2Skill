package com.example.hackofiesta.Database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface VehicleLocationDAO {

    @Insert
    suspend fun insertVehicleLocation(vehicleLocationData: VehicleLocationData)

    @Update
    suspend fun updateVehicleLocation(vehicleLocationData: VehicleLocationData)

    @Delete
    suspend fun deleteVehicleLocation(vehicleLocationData: VehicleLocationData)

    @Query("SELECT * FROM vehicleLocationCountTable WHERE currentCity = :city")
    suspend fun getVehicleLocation(city: String): VehicleLocationData

    @Query("SELECT * FROM vehicleLocationCountTable")
    fun getVehicleLocationAll(): LiveData<List<VehicleLocationData>>
}