package com.courier.trafficpredictor.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Простое локальное хранилище (JSON-файл во внутренней памяти приложения).
 * Никакие данные никуда не отправляются - всё остаётся на телефоне.
 */
object LightsRepository {

    private const val FILE_NAME = "lights_data.json"
    private const val NEAR_LIGHT_RADIUS_M = 45.0 // считаем "тот же светофор" в этом радиусе
    private const val WINDOW_MINUTES = 45 // окно времени для статистики "похожего времени суток"

    val lights: MutableList<TrafficLight> = mutableListOf()
    val passages: MutableList<Passage> = mutableListOf()

    private lateinit var file: File
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        file = File(context.filesDir, FILE_NAME)
        load()
        loaded = true
    }

    @Synchronized
    fun addLightIfNew(lat: Double, lon: Double): TrafficLight {
        findNearest(lat, lon)?.let { existing ->
            if (distanceMeters(lat, lon, existing.lat, existing.lon) <= NEAR_LIGHT_RADIUS_M) {
                return existing
            }
        }
        val newLight = TrafficLight(id = "L${System.currentTimeMillis()}", lat = lat, lon = lon)
        lights.add(newLight)
        save()
        return newLight
    }

    fun findNearest(lat: Double, lon: Double): TrafficLight? {
        return lights.minByOrNull { distanceMeters(lat, lon, it.lat, it.lon) }
    }

    @Synchronized
    fun addPassage(lightId: String, isRed: Boolean, stopDurationSec: Int) {
        passages.add(
            Passage(
                lightId = lightId,
                timestampMillis = System.currentTimeMillis(),
                isRed = isRed,
                stopDurationSec = stopDurationSec
            )
        )
        save()
    }

    /**
     * Прогноз по светофору на основе прошлых проездов в похожее время суток
     * (тот же тип дня: будни/выходные, окно +/- WINDOW_MINUTES минут).
     */
    fun predict(light: TrafficLight, atMillis: Long = System.currentTimeMillis()): Prediction {
        val cal = Calendar.getInstance().apply { timeInMillis = atMillis }
        val targetMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val targetIsWeekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        val relevant = passages.filter { p ->
            if (p.lightId != light.id) return@filter false
            val c = Calendar.getInstance().apply { timeInMillis = p.timestampMillis }
            val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            val isWeekend = c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            isWeekend == targetIsWeekend && minutesDiff(mins, targetMinutes) <= WINDOW_MINUTES
        }

        if (relevant.isEmpty()) {
            return Prediction(light, null, null, 0)
        }

        val greenCount = relevant.count { !it.isRed }
        val greenProb = greenCount.toDouble() / relevant.size
        val reds = relevant.filter { it.isRed }
        val avgStop = if (reds.isNotEmpty()) reds.map { it.stopDurationSec }.average() else null

        return Prediction(light, greenProb, avgStop, relevant.size)
    }

    private fun minutesDiff(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b)
        return minOf(d, 24 * 60 - d)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val lightsArr = root.optJSONArray("lights") ?: JSONArray()
            for (i in 0 until lightsArr.length()) {
                val o = lightsArr.getJSONObject(i)
                lights.add(TrafficLight(o.getString("id"), o.getDouble("lat"), o.getDouble("lon")))
            }
            val passagesArr = root.optJSONArray("passages") ?: JSONArray()
            for (i in 0 until passagesArr.length()) {
                val o = passagesArr.getJSONObject(i)
                passages.add(
                    Passage(
                        o.getString("lightId"),
                        o.getLong("ts"),
                        o.getBoolean("isRed"),
                        o.getInt("stopSec")
                    )
                )
            }
        } catch (e: Exception) {
            // повреждённый файл - начинаем с чистого листа, ничего не роняем
        }
    }

    @Synchronized
    private fun save() {
        val root = JSONObject()
        val lightsArr = JSONArray()
        lights.forEach {
            lightsArr.put(JSONObject().apply {
                put("id", it.id); put("lat", it.lat); put("lon", it.lon)
            })
        }
        val passagesArr = JSONArray()
        passages.forEach {
            passagesArr.put(JSONObject().apply {
                put("lightId", it.lightId); put("ts", it.timestampMillis)
                put("isRed", it.isRed); put("stopSec", it.stopDurationSec)
            })
        }
        root.put("lights", lightsArr)
        root.put("passages", passagesArr)
        file.writeText(root.toString())
    }
}
