package com.xweather.maplibre

/**
 * Typed Xweather Raster Maps layer codes, grouped by category.
 *
 * The full Xweather catalog is 100+ codes (see PLAN.md); this v0.1 subset
 * covers the commonly needed categories (radar, satellite, temperature,
 * precipitation, wind, alerts, admin/base maps). Additional categories are
 * additive — add a new sealed subclass without breaking existing callers.
 * [Custom] is an escape hatch for any layer code not yet modeled here.
 */
sealed class XweatherLayer(val code: String) {

    sealed class Radar(code: String) : XweatherLayer(code) {
        object Standard : Radar("radar")
        object Global : Radar("radar-global")
        object Forecast : Radar("fradar")
    }

    sealed class Satellite(code: String) : XweatherLayer(code) {
        object Infrared : Satellite("satellite")
        object GeoColor : Satellite("satellite-geocolor")
        object Visible : Satellite("satellite-visible")
        object InfraredColor : Satellite("satellite-infrared-color")
        object WaterVapor : Satellite("satellite-water-vapor")
        object Forecast : Satellite("fsatellite")
    }

    sealed class Temperature(code: String) : XweatherLayer(code) {
        object Surface : Temperature("temperatures")
        object SurfaceText : Temperature("temperatures-text")
        object Forecast : Temperature("ftemperatures")
        object ForecastHigh : Temperature("ftemperatures-max")
        object ForecastLow : Temperature("ftemperatures-min")
    }

    sealed class Precipitation(code: String) : XweatherLayer(code) {
        object Accumulated : Precipitation("precip")
        object ForecastHourly : Precipitation("fqpf-1h")
        object ForecastAccumulated : Precipitation("fqpf-accum")
    }

    sealed class Wind(code: String) : XweatherLayer(code) {
        object Speed : Wind("wind-speeds")
        object Gusts : Wind("wind-gusts")
        object Direction : Wind("wind-dir")
    }

    object Alerts : XweatherLayer("alerts")

    sealed class BaseMap(code: String) : XweatherLayer(code) {
        object Flat : BaseMap("flat")
        object FlatDark : BaseMap("flat-dk")
        object Terrain : BaseMap("terrain")
        object BlueMarble : BaseMap("blue-marble")
    }

    sealed class Admin(code: String) : XweatherLayer(code) {
        object Boundaries : Admin("admin")
        object Cities : Admin("admin-cities")
        object States : Admin("states")
        object Counties : Admin("counties")
    }

    /** Escape hatch for layer codes not yet modeled as a typed constant. */
    class Custom(code: String) : XweatherLayer(code)
}
