package com.courier.trafficpredictor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.courier.trafficpredictor.data.LightsRepository
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private var tracking = false
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LightsRepository.init(applicationContext)

        val statusText = findViewById<TextView>(R.id.statusText)
        val statsText = findViewById<TextView>(R.id.statsText)
        val btnOverlay = findViewById<Button>(R.id.btnOverlayPermission)
        val btnStartStop = findViewById<Button>(R.id.btnStartStop)
        val btnMarkLight = findViewById<Button>(R.id.btnMarkLight)

        fun refreshStats() {
            statsText.text = "Светофоров отмечено: ${LightsRepository.lights.size}\n" +
                "Записей проезда: ${LightsRepository.passages.size}"
        }
        refreshStats()

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Разрешение уже есть", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartStop.setOnClickListener {
            if (!tracking) {
                if (!hasLocationPermissions()) {
                    requestLocationPermissions()
                    return@setOnClickListener
                }
                val intent = Intent(this, TrackingService::class.java)
                ContextCompat.startForegroundService(this, intent)
                tracking = true
                btnStartStop.text = "Остановить запись"
                statusText.text = "Статус: запись идёт"
            } else {
                stopService(Intent(this, TrackingService::class.java))
                tracking = false
                btnStartStop.text = "2. Начать запись поездки"
                statusText.text = "Статус: остановлено"
            }
        }

        btnMarkLight.setOnClickListener {
            if (!hasLocationPermissions()) {
                requestLocationPermissions()
                return@setOnClickListener
            }
            val fused = LocationServices.getFusedLocationProviderClient(this)
            try {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        LightsRepository.addLightIfNew(loc.latitude, loc.longitude)
                        refreshStats()
                        Toast.makeText(this, "Светофор отмечен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Не удалось получить координаты, попробуйте ещё раз", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                requestLocationPermissions()
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && background
    }

    private fun requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Сначала обычный доступ, потом отдельным диалогом - фоновый (так требует Android)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_REQUEST_CODE + 1
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE + 2
            )
        }
    }
}
