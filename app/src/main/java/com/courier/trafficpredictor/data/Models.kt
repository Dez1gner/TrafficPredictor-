package com.courier.trafficpredictor.data

data class TrafficLight(
    val id: String,
    val lat: Double,
    val lon: Double
)

data class Passage(
    val lightId: String,
    val timestampMillis: Long,
    val isRed: Boolean,
    val stopDurationSec: Int
)

data class Prediction(
    val light: TrafficLight,
    val greenProbability: Double?, // null = недостаточно данных
    val avgStopDurationSec: Double?,
    val sampleSize: Int
)
