package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherClientTest {

    @Test
    fun `parses open-meteo response`() {
        val body = """
            {
              "latitude": 23.75,
              "longitude": 90.375,
              "current": {
                "temperature_2m": 31.4,
                "relative_humidity_2m": 78,
                "apparent_temperature": 38.2,
                "is_day": 1,
                "weather_code": 80,
                "wind_speed_10m": 11.5,
                "wind_direction_10m": 135,
                "surface_pressure": 1004.2,
                "cloud_cover": 75,
                "precipitation": 0.4
              },
              "daily": {
                "temperature_2m_max": [33.1],
                "temperature_2m_min": [27.0],
                "uv_index_max": [8.5],
                "precipitation_probability_max": [65],
                "sunrise": ["2026-07-19T05:16"],
                "sunset": ["2026-07-19T18:49"]
              }
            }
        """.trimIndent()
        val info = WeatherClient.parse(body, now = 42L)
        assertEquals(31.4, info.temperatureC, 1e-9)
        assertEquals(38.2, info.feelsLikeC, 1e-9)
        assertEquals(78, info.humidityPercent)
        assertEquals(11.5, info.windKmh, 1e-9)
        assertEquals(135, info.windDirectionDeg)
        assertEquals(1004.2, info.pressureHpa, 1e-9)
        assertEquals(75, info.cloudCoverPercent)
        assertEquals(0.4, info.precipitationMm, 1e-9)
        assertEquals(80, info.weatherCode)
        assertEquals(true, info.isDay)
        assertEquals(33.1, info.highC, 1e-9)
        assertEquals(27.0, info.lowC, 1e-9)
        assertEquals(8.5, info.uvIndexMax, 1e-9)
        assertEquals(65, info.precipProbabilityPercent)
        assertEquals("05:16", info.sunrise)
        assertEquals("18:49", info.sunset)
        assertEquals(42L, info.fetchedAtMillis)
    }

    @Test
    fun `parses response without the optional fields`() {
        val body = """
            {
              "current": {
                "temperature_2m": 10.0,
                "relative_humidity_2m": 50,
                "apparent_temperature": 9.0,
                "is_day": 0,
                "weather_code": 3,
                "wind_speed_10m": 5.0
              },
              "daily": {
                "temperature_2m_max": [12.0],
                "temperature_2m_min": [4.0],
                "uv_index_max": [null],
                "precipitation_probability_max": [null]
              }
            }
        """.trimIndent()
        val info = WeatherClient.parse(body, now = 1L)
        assertEquals(0, info.windDirectionDeg)
        assertEquals(-1.0, info.pressureHpa, 1e-9)
        assertEquals(-1, info.cloudCoverPercent)
        assertEquals(-1.0, info.uvIndexMax, 1e-9)
        assertEquals(-1, info.precipProbabilityPercent)
        assertEquals("", info.sunrise)
        assertEquals("", info.sunset)
    }

    @Test
    fun `wind cardinal`() {
        assertEquals("N", WeatherClient.windCardinal(0))
        assertEquals("SE", WeatherClient.windCardinal(135))
        assertEquals("N", WeatherClient.windCardinal(350))
        assertEquals("W", WeatherClient.windCardinal(280))
    }

    @Test
    fun `celsius fahrenheit display`() {
        assertEquals(31, WeatherClient.toDisplay(31.4, fahrenheit = false))
        assertEquals(89, WeatherClient.toDisplay(31.4, fahrenheit = true))
        assertEquals(32, WeatherClient.toDisplay(0.0, fahrenheit = true))
    }
}
