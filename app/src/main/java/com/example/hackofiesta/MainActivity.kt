package com.example.hackofiesta

import ai.onnxruntime.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hackofiesta.Database.OverallDatabase
import com.example.hackofiesta.Database.VehicleLocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    lateinit var toggle: ToggleButton
    lateinit var log: TextView
    lateinit var preview: PreviewView

    var timer: Timer? = null
    val images = mutableListOf<Bitmap>()
    val seenPlates = mutableSetOf<String>()

    val allDetectedPlates = mutableListOf<Pair<String, String>>()
    var maxVehicles = 0

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    data class Detection(val box: RectF, val score: Float, val classId: Int)

    lateinit var aiBtn: MaterialButton

    val env = OrtEnvironment.getEnvironment()
    lateinit var vehicleModel: OrtSession
    lateinit var trafficModel: OrtSession
    lateinit var plateModel: OrtSession

    lateinit var database : OverallDatabase

    val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        database = OverallDatabase.getDatabase(this)

        val materialToolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.materialToolbar)
        materialToolbar.setNavigationOnClickListener { finish() }

        val backBtn = findViewById<MaterialButton>(R.id.backBtn)

        backBtn.setOnClickListener {
            finish()
        }

        toggle = findViewById(R.id.tgl)
        val tglOn = findViewById<MaterialButton>(R.id.tglOn)
        val tglOff = findViewById<MaterialButton>(R.id.tglOff)
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)

        log = findViewById(R.id.log)
        preview = findViewById(R.id.previewView)
        aiBtn = findViewById<MaterialButton>(R.id.aiBtn)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkPermission()
        loadModels()

        toggleGroup.check(R.id.tglOff)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.tglOn) {
                    toggle.isChecked = true
                    startScanning()
                } else if (checkedId == R.id.tglOff) {
                    toggle.isChecked = false
                    stopScanning()
                }
            }
        }

        aiBtn.setOnClickListener {
            val platesInfo = if (allDetectedPlates.isEmpty()) {
                "No license plates were identified during this scan."
            } else {
                "Identified License Plates:\n" +
                        allDetectedPlates.joinToString("\n") { (plate, state) -> "- Plate: $plate, State: $state" }
            }

            val prompt = """
            OFFICIAL TRAFFIC MONITORING REPORT REQUEST
            
            SOURCE DATA:
            $platesInfo
            PEAK VEHICLE DENSITY: $maxVehicles vehicles detected in a single frame.
            
            TASK:
            Generate a formal executive summary. 
            
            STRICT FORMATTING CONSTRAINTS:
            1. DO NOT use markdown symbols like asterisks (*) or hashtags (#).
            2. DO NOT use bold or italic formatting.
            3. Use plain text only.
            4. Use ALL CAPS for section headers.
            5. Maintain a professional administrative tone.
            """.trimIndent()

            log.text = "Generating Formal AI Report..."
            askGemini(prompt)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    fun checkPermission() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        } else {
            if (permissions.size > 1) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
            }
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val cameraIndex = permissions.indexOf(Manifest.permission.CAMERA)
        if (cameraIndex != -1 && grantResults[cameraIndex] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase
            )
        }, ContextCompat.getMainExecutor(this))
    }

    fun loadModels() {
        try {
            vehicleModel = env.createSession(assets.open("yolov8n.onnx").readBytes())
            trafficModel = env.createSession(assets.open("mio_tcd.onnx").readBytes())
            plateModel = env.createSession(assets.open("license_plate_detector.onnx").readBytes())

            logModelInfo("Vehicle", vehicleModel)
            logModelInfo("Traffic", trafficModel)
            logModelInfo("Plate", plateModel)
        } catch (e: Exception) {
            log.text = "Model error: ${e.message}"
        }
    }

    private fun logModelInfo(name: String, session: OrtSession) {
        val inputInfo = session.inputInfo
        val outputInfo = session.outputInfo
        Log.d("ModelInfo", "--- $name Model ---")
        inputInfo.forEach { (k, v) -> Log.d("ModelInfo", "Input: $k, Info: $v") }
        outputInfo.forEach { (k, v) -> Log.d("ModelInfo", "Output: $k, Info: $v") }
    }

    fun startScanning() {
        synchronized(images) {
            images.clear()
        }
        seenPlates.clear()
        allDetectedPlates.clear()
        maxVehicles = 0
        log.text = "Scanning..."

        timer = Timer()
        timer?.scheduleAtFixedRate(timerTask {
            captureFrame()
        }, 0, 1000)
    }

    fun stopScanning() {
        timer?.cancel()
        val count = synchronized(images) { images.size }
        log.text = "Processing $count images..."
        if (count == 0) {
            log.append("\nNo images captured! check camera.")
        }
        processImages()
    }

    fun captureFrame() {
        runOnUiThread {
            val bmp = preview.bitmap ?: return@runOnUiThread

            Thread {
                val vehicleCount = runModel(vehicleModel, bmp, 640)
                val isTraffic = runMioModel(trafficModel, bmp)

                if (vehicleCount > 0 && isTraffic) {
                    if (vehicleCount > maxVehicles) maxVehicles = vehicleCount
                    runOnUiThread {
                        if (toggle.isChecked) {
                            log.text = "Scanning... Current: $vehicleCount | Max: $maxVehicles"
                        }
                    }
                    synchronized(images) {
                        if (images.size < 10) {
                            images.add(bmp)
                        }
                    }
                }
            }.start()
        }
    }

    fun processImages() {
        val platePattern = Regex("[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{1,4}")
        val stateMap = mapOf(
            "AN" to "Andaman & Nicobar", "AP" to "Andhra Pradesh", "AR" to "Arunachal Pradesh",
            "AS" to "Assam", "BR" to "Bihar", "CH" to "Chandigarh", "CG" to "Chhattisgarh",
            "DN" to "Dadra & Nagar Haveli", "DD" to "Daman & Diu", "DL" to "Delhi",
            "GA" to "Goa", "GJ" to "Gujarat", "HR" to "Haryana", "HP" to "Himachal Pradesh",
            "JK" to "Jammu & Kashmir", "JH" to "Jharkhand", "KA" to "Karnataka",
            "KL" to "Kerala", "LD" to "Lakshadweep", "MP" to "Madhya Pradesh",
            "MH" to "Maharashtra", "MN" to "Manipur", "ML" to "Meghalaya",
            "MZ" to "Mizoram", "NL" to "Nagaland", "OD" to "Odisha", "PY" to "Puducherry",
            "PB" to "Punjab", "RJ" to "Rajasthan", "SK" to "Sikkim", "TN" to "Tamil Nadu",
            "TS" to "Telangana", "TR" to "Tripura", "UP" to "Uttar Pradesh",
            "UK" to "Uttarakhand", "WB" to "West Bengal"
        )

        val imagesToProcess = synchronized(images) { images.toList() }
        if (imagesToProcess.isEmpty()) {
            showSummaryIfNoPlates()
            return
        }

        var pendingTasks = 0
        val tasksToLaunch = mutableListOf<Pair<Bitmap, List<RectF>>>()

        for (img in imagesToProcess) {
            val plateBoxes = detectPlates(plateModel, img)
            tasksToLaunch.add(img to plateBoxes)
            pendingTasks += plateBoxes.size
        }

        if (pendingTasks == 0) {
            showSummaryIfNoPlates()
            return
        }

        for ((img, plateBoxes) in tasksToLaunch) {
            for (plateBox in plateBoxes) {
                val croppedPlate = cropBitmap(img, plateBox)
                val input = InputImage.fromBitmap(croppedPlate, 0)

                ocr.process(input).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val result = task.result
                        val cleanedText = result.text.uppercase().replace(Regex("[^A-Z0-9]"), "")
                        val matches = platePattern.findAll(cleanedText)

                        var bestCandidate: String? = null
                        for (match in matches) {
                            val plate = match.value
                            if (plate.length >= 7) {
                                if (bestCandidate == null || plate.length > bestCandidate!!.length) {
                                    bestCandidate = plate
                                }
                            }
                        }

                        if (bestCandidate == null && cleanedText.length in 7..11) {
                            bestCandidate = cleanedText
                        }

                        bestCandidate?.let { plate ->
                            if (isNewAndUnique(plate)) {
                                val stateCode = plate.substring(0, 2)
                                val stateName = stateMap[stateCode] ?: "Other State"
                                val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                allDetectedPlates.add(Pair(plate, stateName))
                                runOnUiThread { log.append("\n[$time] $plate ($stateName)") }
                            }
                        }
                    }

                    synchronized(this) {
                        pendingTasks--
                        if (pendingTasks == 0) {
                            showSummaryIfNoPlates()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSummaryIfNoPlates() {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                        val city = if (addresses?.isNotEmpty() == true) addresses[0].locality ?: "Unknown City" else "Unknown Location"
                        val state = if (addresses?.isNotEmpty() == true) addresses[0].adminArea ?: "Unknown State" else "Unknown Location"
                        val locationName = if (addresses?.isNotEmpty() == true) {
                            addresses[0].getAddressLine(0)
                        } else {
                            "Lat: ${location.latitude}, Lon: ${location.longitude}"
                        }
                        updateLogSummary(locationName, time, city, state, location.latitude, location.longitude)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = try {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    } catch (e: Exception) {
                        null
                    }
                    val city = if (addresses?.isNotEmpty() == true) addresses[0].locality ?: "Unknown City" else "Unknown Location"
                    val state = if (addresses?.isNotEmpty() == true) addresses[0].adminArea ?: "Unknown State" else "Unknown Location"
                    val locationName = if (addresses?.isNotEmpty() == true) {
                        addresses[0].getAddressLine(0)
                    } else {
                        "Lat: ${location.latitude}, Lon: ${location.longitude}"
                    }
                    updateLogSummary(locationName, time, city, state, location.latitude, location.longitude)
                }
            } else {
                updateLogSummary("Location Unavailable", time, "Unknown City", "Unknown State", 0.0, 0.0)
            }
        }.addOnFailureListener {
            updateLogSummary("Permission denied/Unavailable", time, "Unknown City", "Unknown State", 0.0, 0.0)
        }
    }

    private fun updateLogSummary(locationName: String, time: String, city : String, state : String, lat: Double, lon: Double) {
        updateDatabase(city, state, maxVehicles, allDetectedPlates.size, lat, lon)
        runOnUiThread {
            log.append("\n\n--- FINAL SCAN SUMMARY ---")
            log.append("\nMax Vehicles Detected: $maxVehicles")
            log.append("\nPlates Detected: ${allDetectedPlates.size}")
            log.append("\nLocation: $locationName")
            log.append("\nTime: $time")
            if (allDetectedPlates.isEmpty()) {
                log.append("\nStatus: No license plates detected in this session.")
            }
        }
    }

    private fun isNewAndUnique(newPlate: String): Boolean {
        synchronized(seenPlates) {
            for (seen in seenPlates) {
                if (seen == newPlate) return false

                if (levenshteinDistance(newPlate, seen) <= 2) {
                    return false
                }
            }
            seenPlates.add(newPlate)
            return true
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap {
        val left = max(0f, rect.left * bitmap.width).toInt()
        val top = max(0f, rect.top * bitmap.height).toInt()
        val width = min(bitmap.width - left, (rect.width() * bitmap.width).toInt())
        val height = min(bitmap.height - top, (rect.height() * bitmap.height).toInt())

        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            bitmap
        }
    }

    fun detectPlates(session: OrtSession, bmp: Bitmap): List<RectF> {
        val size = 320
        val resized = Bitmap.createScaledBitmap(bmp, size, size, true)
        val buffer = bitmapToBuffer(resized, size)

        val tensor =
            OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val output = session.run(mapOf(session.inputNames.first() to tensor))

        val rawOutput = (output.get(0).value as Array<Array<FloatArray>>)[0]
        val detectedBoxes = mutableListOf<RectF>()

        for (i in 0 until rawOutput[0].size) {
            val conf = if (rawOutput.size > 4) rawOutput[4][i] else 1f
            if (conf > 0.55f) {
                val cx = rawOutput[0][i] / size
                val cy = rawOutput[1][i] / size
                val w = rawOutput[2][i] / size
                val h = rawOutput[3][i] / size
                detectedBoxes.add(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
            }
        }

        tensor.close()
        output.close()

        return detectedBoxes.distinctBy { "${(it.left * 10).toInt()}_${(it.top * 10).toInt()}" }
    }

    fun runModel(session: OrtSession, bmp: Bitmap, size: Int): Int {
        val resized = Bitmap.createScaledBitmap(bmp, size, size, true)
        val buffer = bitmapToBuffer(resized, size)
        val tensor =
            OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val result = session.run(mapOf(session.inputNames.first() to tensor))

        val rawOutput = (result.get(0).value as Array<Array<FloatArray>>)[0]
        val detections = mutableListOf<Detection>()
        val vehicleClasses = setOf(2, 3, 5, 7) // COCO: car, motorcycle, bus, truck

        for (i in 0 until rawOutput[0].size) {
            var maxConf = 0f
            var classId = -1
            for (c in 4 until rawOutput.size) {
                if (rawOutput[c][i] > maxConf) {
                    maxConf = rawOutput[c][i]
                    classId = c - 4
                }
            }

            if (maxConf > 0.45f && classId in vehicleClasses) {
                val cx = rawOutput[0][i] / size
                val cy = rawOutput[1][i] / size
                val w = rawOutput[2][i] / size
                val h = rawOutput[3][i] / size
                detections.add(Detection(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), maxConf, classId))
            }
        }

        val count = nms(detections).size
        tensor.close()
        result.close()
        return count
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val res = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            res.add(best)
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (iou(best.box, next.box) > 0.45f) {
                    iterator.remove()
                }
            }
        }
        return res
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(a, b)) return 0f
        val intersectArea = intersection.width() * intersection.height()
        val unionArea = a.width() * a.height() + b.width() * b.height() - intersectArea
        return if (unionArea > 0) intersectArea / unionArea else 0f
    }

    fun runMioModel(session: OrtSession, bmp: Bitmap): Boolean {
        val size = 128
        val resized = Bitmap.createScaledBitmap(bmp, size, size, true)
        val buffer = bitmapToBuffer(resized, size)

        val tensor = OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, 3, size.toLong(), size.toLong())
        )

        val result = session.run(mapOf(session.inputNames.first() to tensor))

        tensor.close()
        result.close()

        return true
    }

    fun bitmapToBuffer(bmp: Bitmap, size: Int): FloatBuffer {
        val buffer = FloatBuffer.allocate(3 * size * size)
        val pixels = IntArray(size * size)

        bmp.getPixels(pixels, 0, size, 0, 0, size, size)

        for (p in pixels) buffer.put(((p shr 16) and 0xFF) / 255f)
        for (p in pixels) buffer.put(((p shr 8) and 0xFF) / 255f)
        for (p in pixels) buffer.put((p and 0xFF) / 255f)

        buffer.rewind()
        return buffer
    }

    fun askGemini(prompt: String) {
        Thread {
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
                    val rawAiText = responseJson.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    // Strip markdown symbols (* and #) and normalize the layout
                    val cleanText = rawAiText
                        .replace(Regex("[*#]"), "")
                        .replace(Regex("(?m)^[ \t]*-[ \t]*"), "  • ")
                        .trim()

                    runOnUiThread {
                        log.text = "✨ OFFICIAL TRAFFIC ANALYSIS REPORT\n\n$cleanText"
                    }
                } else {
                    runOnUiThread {
                        log.text = "Gemini Error: ${response.code}\n$responseBody"
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    log.text = "Gemini error: ${e.message}"
                }
            }
        }.start()
    }

    fun updateDatabase(city : String, state : String, maxVehiclesCount : Int, platesDetected: Int, lat: Double, lon: Double){
        lifecycleScope.launch(Dispatchers.IO){
            val existing = database.vehicleLocationDao().getVehicleLocation(city);
            if (existing != null){
                var currentVal = existing.vehicleCount!!;
                currentVal += maxVehiclesCount;
                var currentPlates = existing.plateCount ?: 0
                currentPlates += platesDetected
                val currentId = existing.id;
                val updatedData = VehicleLocationData(currentId,state,city,currentVal, currentPlates, lat, lon);
                database.vehicleLocationDao().updateVehicleLocation(updatedData);
            }
            else{
                val newData = VehicleLocationData(0,state,city,maxVehiclesCount, platesDetected, lat, lon);
                database.vehicleLocationDao().insertVehicleLocation(newData);
            }
        }
    }


}
