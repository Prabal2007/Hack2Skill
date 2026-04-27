package com.example.hackofiesta

import ai.onnxruntime.*
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.ToggleButton
import com.example.hackofiesta.BuildConfig
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

    lateinit var aiBtn: MaterialButton

    val env = OrtEnvironment.getEnvironment()
    lateinit var vehicleModel: OrtSession
    lateinit var trafficModel: OrtSession
    lateinit var plateModel: OrtSession

    val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.tgl)
        log = findViewById(R.id.log)
        preview = findViewById(R.id.previewView)

        checkPermission()
        loadModels()

        toggle.setOnCheckedChangeListener { _, isOn ->
            if (isOn) startScanning() else stopScanning()
        }

        aiBtn = findViewById<MaterialButton>(R.id.aiBtn)

        aiBtn.setOnClickListener {
            if (allDetectedPlates.isEmpty()) {
                log.append("\nNo plates detected yet")
                return@setOnClickListener
            }

            val platesInfo = allDetectedPlates.joinToString("\n") { (plate, state) ->
                "- Plate: $plate, State: $state"
            }

            val prompt = """
            The following license plates were detected:
            $platesInfo
            
            Provide comprehensive traffic analysis and registration insights for all these vehicles.
            """.trimIndent()

            askGemini(prompt)
        }
    }

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
                val isVehicle = runModel(vehicleModel, bmp, 640)
                val isTraffic = runMioModel(trafficModel, bmp)

                if (isVehicle && isTraffic) {
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

        for (img in imagesToProcess) {
            val plateBoxes = detectPlates(plateModel, img)
            for (plateBox in plateBoxes) {
                val croppedPlate = cropBitmap(img, plateBox)
                val input = InputImage.fromBitmap(croppedPlate, 0)

                ocr.process(input).addOnSuccessListener { result ->
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
                            val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date())

                            allDetectedPlates.add(Pair(plate, stateName))

                            runOnUiThread {
                                log.append("\n[$time] $plate ($stateName)")
                            }
                        }
                    }
                }
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
            if (conf > 0.55f) { // High threshold to avoid noise
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

    fun runModel(session: OrtSession, bmp: Bitmap, size: Int): Boolean {
        val resized = Bitmap.createScaledBitmap(bmp, size, size, true)
        val buffer = bitmapToBuffer(resized, size)
        val tensor =
            OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val result = session.run(mapOf(session.inputNames.first() to tensor))

        val detected = true

        tensor.close()
        result.close()
        return detected
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
                    val aiText = responseJson.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    runOnUiThread {
                        log.append("\n\n✨ AI Insight:\n$aiText")
                    }
                } else {
                    runOnUiThread {
                        log.append("\nGemini Error: ${response.code}\n$responseBody")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    log.append("\nGemini error: ${e.message}")
                }
            }
        }.start()
    }
}
