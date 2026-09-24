package com.courier.trafficpredictor

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.courier.trafficpredictor.data.LightsRepository
import com.google.android.gms.location.*
import java.util.Locale

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIF_ID = 1

        const val APPROACH_RADIUS = 250.0
        const val STOP_RADIUS = 35.0
        const val EXIT_RADIUS = 60.0
        const val SPEED_STOP_THRESHOLD = 1.3
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var overlay: OverlayManager

    private var activeLightId: String? = null
    private var everWasClose = false
    private var sawRed = false
    private var stopStartMillis: Long? = null
    private var stopAccumSec = 0

    private var lastDepartureLightId: String? = null
    private var lastDepartureTimeMillis: Long? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LightsRepository.init(applicationContext)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        overlay = OverlayManager(
            applicationContext,
            onMarkLight = { markLightAtCurrentLocation() },
            onStop = { stopSelf() }
        )
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Слежение включено"))
        startLocationUpdates()
        overlay.showPanel()
        overlay.setStatus("Едем...", "")
        return START_STICKY
    }

    private fun markLightAtCurrentLocation() {
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    LightsRepository.addLightIfNew(loc.latitude, loc.longitude)
                    overlay.setStatus("Отмечено ✓", "")
                }
            }
        } catch (e: SecurityException) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        overlay.hide()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) { }
    }

    private fun onNewLocation(loc: Location) {
        val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
        val nearest = LightsRepository.findNearest(loc.latitude, loc.longitude)

        if (nearest == null) {
            overlay.setStatus("Нет светофоров", "начни отмечать кнопкой")
            return
        }

        val dist = LightsRepository.distanceMeters(loc.latitude, loc.longitude, nearest.lat, nearest.lon)

        if (dist <= APPROACH_RADIUS) {
            if (activeLightId != nearest.id) {
                activeLightId = nearest.id
                everWasClose = false
                sawRed = false
                stopStartMillis = null
                stopAccumSec = 0
            }
            if (dist <= STOP_RADIUS) {
                everWasClose = true
                if (speed < SPEED_STOP_THRESHOLD) {
                    if (stopStartMillis == null) stopStartMillis = System.currentTimeMillis()
                    sawRed = true
                } else if (stopStartMillis != null) {
                    stopAccumSec += ((System.currentTimeMillis() - stopStartMillis!!) / 1000).toInt()
                    stopStartMillis = null
                }
            }
            updateOverlayForLight(nearest, dist, speed)
        } else if (dist > EXIT_RADIUS && activeLightId == nearest.id) {
            if (everWasClose) {
                if (stopStartMillis != null) {
                    stopAccumSec += ((System.currentTimeMillis() - stopStartMillis!!) / 1000).toInt()
                    stopStartMillis = null
                }
                val departureNow = System.currentTimeMillis()
                val secondsSincePrev = lastDepartureTimeMillis?.let {
                    ((departureNow - it) / 1000).toInt()
                }
                LightsRepository.addPassage(
                    nearest.id, sawRed, stopAccumSec,
                    prevLightId = lastDepartureLightId,
                    secondsSincePrev = secondsSincePrev
                )
                lastDepartureLightId = nearest.id
                lastDepartureTimeMillis = departureNow
            }
            activeLightId = null
            everWasClose = false
            overlay.setStatus("Едем дальше", "")
        } else {
            overlay.setStatus(String.format(Locale.getDefault(), "%.0f м", dist), "до следующего светофора")
        }
    }

    private fun updateOverlayForLight(light: com.courier.trafficpredictor.data.TrafficLight, dist: Double, speedMs: Double) {
        val secondsSincePrevNow = lastDepartureTimeMillis?.let {
            ((System.currentTimeMillis() - it) / 1000).toInt()
        }
        val advice = LightsRepository.pacingAdvice(
            light = light,
            prevLightId = lastDepartureLightId,
            secondsSincePrevNow = secondsSincePrevNow,
            remainingDistanceM = dist,
            currentSpeedMs = speedMs
        )
        val distText = String.format(Locale.getDefault(), "%.0f м • ", dist)
        overlay.setStatus(advice.headline, distText + advice.detail)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Слежение за маршрутом", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Светофоры Курьера")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
