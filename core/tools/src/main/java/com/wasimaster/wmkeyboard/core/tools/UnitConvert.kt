package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R

/**
 * Unit-conversion catalog and math for the converter tool. Each unit maps
 * to its category's base unit as `base = value * factor + offset`
 * (offset only for temperatures); [ConvUnit.reciprocal] marks fuel-economy
 * style units where `base = factor / value`. All local, no network.
 *
 * Names are string resources: the UI resolves [ConvUnit.nameRes] and
 * [Category.nameRes] where it draws them. Symbols stay literals, since a
 * symbol is the same in every language.
 */
object UnitConvert {

    data class ConvUnit(
        @StringRes val nameRes: Int,
        val symbol: String,
        val factor: Double,
        val offset: Double = 0.0,
        val reciprocal: Boolean = false,
    )

    /**
     * [id] is the stored key: the converter remembers a unit pair per category
     * under it, and a smart-suggestion chip carries it into the panel. It is
     * never shown, is never translated, and must not change once shipped.
     */
    data class Category(
        val id: String,
        @StringRes val nameRes: Int,
        val units: List<ConvUnit>,
    )

    fun convert(value: Double, from: ConvUnit, to: ConvUnit): Double {
        val base = if (from.reciprocal) {
            if (value == 0.0) return Double.NaN else from.factor / value
        } else {
            value * from.factor + from.offset
        }
        return if (to.reciprocal) {
            if (base == 0.0) Double.NaN else to.factor / base
        } else {
            (base - to.offset) / to.factor
        }
    }

    val categories: List<Category> = listOf(
        Category(
            "Length", R.string.core_tools_unit_category_length_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_millimetre_label, "mm", 0.001),
                ConvUnit(R.string.core_tools_unit_centimetre_label, "cm", 0.01),
                ConvUnit(R.string.core_tools_unit_metre_label, "m", 1.0),
                ConvUnit(R.string.core_tools_unit_kilometre_label, "km", 1000.0),
                ConvUnit(R.string.core_tools_unit_inch_label, "in", 0.0254),
                ConvUnit(R.string.core_tools_unit_foot_label, "ft", 0.3048),
                ConvUnit(R.string.core_tools_unit_yard_label, "yd", 0.9144),
                ConvUnit(R.string.core_tools_unit_mile_label, "mi", 1609.344),
                ConvUnit(R.string.core_tools_unit_nautical_mile_label, "nmi", 1852.0),
                ConvUnit(R.string.core_tools_unit_micrometre_label, "µm", 1e-6),
                ConvUnit(R.string.core_tools_unit_nanometre_label, "nm", 1e-9),
                ConvUnit(R.string.core_tools_unit_light_year_label, "ly", 9.4607304725808e15),
            ),
        ),
        Category(
            "Mass", R.string.core_tools_unit_category_mass_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_milligram_label, "mg", 1e-6),
                ConvUnit(R.string.core_tools_unit_gram_label, "g", 0.001),
                ConvUnit(R.string.core_tools_unit_kilogram_label, "kg", 1.0),
                ConvUnit(R.string.core_tools_unit_tonne_label, "t", 1000.0),
                ConvUnit(R.string.core_tools_unit_ounce_label, "oz", 0.028349523125),
                ConvUnit(R.string.core_tools_unit_pound_label, "lb", 0.45359237),
                ConvUnit(R.string.core_tools_unit_stone_label, "st", 6.35029318),
                ConvUnit(R.string.core_tools_unit_us_ton_label, "ton", 907.18474),
                ConvUnit(R.string.core_tools_unit_carat_label, "ct", 0.0002),
            ),
        ),
        Category(
            "Temperature", R.string.core_tools_unit_category_temperature_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_celsius_label, "°C", 1.0, 273.15),
                ConvUnit(
                    R.string.core_tools_unit_fahrenheit_label,
                    "°F",
                    5.0 / 9.0,
                    273.15 - 32 * 5.0 / 9.0,
                ),
                ConvUnit(R.string.core_tools_unit_kelvin_label, "K", 1.0),
            ),
        ),
        Category(
            "Area", R.string.core_tools_unit_category_area_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_square_millimetre_label, "mm²", 1e-6),
                ConvUnit(R.string.core_tools_unit_square_centimetre_label, "cm²", 1e-4),
                ConvUnit(R.string.core_tools_unit_square_metre_label, "m²", 1.0),
                ConvUnit(R.string.core_tools_unit_hectare_label, "ha", 10_000.0),
                ConvUnit(R.string.core_tools_unit_square_kilometre_label, "km²", 1e6),
                ConvUnit(R.string.core_tools_unit_square_inch_label, "in²", 0.00064516),
                ConvUnit(R.string.core_tools_unit_square_foot_label, "ft²", 0.09290304),
                ConvUnit(R.string.core_tools_unit_square_yard_label, "yd²", 0.83612736),
                ConvUnit(R.string.core_tools_unit_acre_label, "ac", 4046.8564224),
                ConvUnit(R.string.core_tools_unit_square_mile_label, "mi²", 2_589_988.110336),
                ConvUnit(R.string.core_tools_unit_katha_label, "katha", 66.8902),
                ConvUnit(R.string.core_tools_unit_bigha_label, "bigha", 1337.803),
            ),
        ),
        Category(
            "Volume", R.string.core_tools_unit_category_volume_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_millilitre_label, "mL", 0.001),
                ConvUnit(R.string.core_tools_unit_litre_label, "L", 1.0),
                ConvUnit(R.string.core_tools_unit_cubic_metre_label, "m³", 1000.0),
                ConvUnit(R.string.core_tools_unit_teaspoon_us_label, "tsp", 0.00492892159375),
                ConvUnit(R.string.core_tools_unit_tablespoon_us_label, "tbsp", 0.01478676478125),
                ConvUnit(R.string.core_tools_unit_fluid_ounce_us_label, "fl oz", 0.0295735295625),
                ConvUnit(R.string.core_tools_unit_cup_us_label, "cup", 0.2365882365),
                ConvUnit(R.string.core_tools_unit_pint_us_label, "pt", 0.473176473),
                ConvUnit(R.string.core_tools_unit_quart_us_label, "qt", 0.946352946),
                ConvUnit(R.string.core_tools_unit_gallon_us_label, "gal", 3.785411784),
                ConvUnit(R.string.core_tools_unit_gallon_uk_label, "gal UK", 4.54609),
                ConvUnit(R.string.core_tools_unit_cubic_inch_label, "in³", 0.016387064),
                ConvUnit(R.string.core_tools_unit_cubic_foot_label, "ft³", 28.316846592),
            ),
        ),
        Category(
            "Speed", R.string.core_tools_unit_category_speed_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_metres_per_second_label, "m/s", 1.0),
                ConvUnit(R.string.core_tools_unit_kilometres_per_hour_label, "km/h", 1 / 3.6),
                ConvUnit(R.string.core_tools_unit_miles_per_hour_label, "mph", 0.44704),
                ConvUnit(R.string.core_tools_unit_knot_label, "kn", 1852.0 / 3600.0),
                ConvUnit(R.string.core_tools_unit_feet_per_second_label, "ft/s", 0.3048),
                ConvUnit(R.string.core_tools_unit_mach_label, "Mach", 340.29),
            ),
        ),
        Category(
            "Time", R.string.core_tools_unit_category_time_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_millisecond_label, "ms", 0.001),
                ConvUnit(R.string.core_tools_unit_second_label, "s", 1.0),
                ConvUnit(R.string.core_tools_unit_minute_label, "min", 60.0),
                ConvUnit(R.string.core_tools_unit_hour_label, "h", 3600.0),
                ConvUnit(R.string.core_tools_unit_day_label, "d", 86_400.0),
                ConvUnit(R.string.core_tools_unit_week_label, "wk", 604_800.0),
                ConvUnit(R.string.core_tools_unit_month_label, "mo", 2_629_746.0),
                ConvUnit(R.string.core_tools_unit_year_label, "yr", 31_556_952.0),
            ),
        ),
        Category(
            "Data", R.string.core_tools_unit_category_data_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_bit_label, "bit", 0.125),
                ConvUnit(R.string.core_tools_unit_byte_label, "B", 1.0),
                ConvUnit(R.string.core_tools_unit_kilobyte_label, "kB", 1e3),
                ConvUnit(R.string.core_tools_unit_megabyte_label, "MB", 1e6),
                ConvUnit(R.string.core_tools_unit_gigabyte_label, "GB", 1e9),
                ConvUnit(R.string.core_tools_unit_terabyte_label, "TB", 1e12),
                ConvUnit(R.string.core_tools_unit_kibibyte_label, "KiB", 1024.0),
                ConvUnit(R.string.core_tools_unit_mebibyte_label, "MiB", 1_048_576.0),
                ConvUnit(R.string.core_tools_unit_gibibyte_label, "GiB", 1_073_741_824.0),
                ConvUnit(R.string.core_tools_unit_tebibyte_label, "TiB", 1_099_511_627_776.0),
            ),
        ),
        Category(
            "Energy", R.string.core_tools_unit_category_energy_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_joule_label, "J", 1.0),
                ConvUnit(R.string.core_tools_unit_kilojoule_label, "kJ", 1000.0),
                ConvUnit(R.string.core_tools_unit_calorie_label, "cal", 4.184),
                ConvUnit(R.string.core_tools_unit_kilocalorie_label, "kcal", 4184.0),
                ConvUnit(R.string.core_tools_unit_watt_hour_label, "Wh", 3600.0),
                ConvUnit(R.string.core_tools_unit_kilowatt_hour_label, "kWh", 3_600_000.0),
                ConvUnit(R.string.core_tools_unit_btu_label, "BTU", 1055.05585262),
                ConvUnit(R.string.core_tools_unit_electronvolt_label, "eV", 1.602176634e-19),
            ),
        ),
        Category(
            "Power", R.string.core_tools_unit_category_power_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_watt_label, "W", 1.0),
                ConvUnit(R.string.core_tools_unit_kilowatt_label, "kW", 1000.0),
                ConvUnit(R.string.core_tools_unit_megawatt_label, "MW", 1e6),
                ConvUnit(R.string.core_tools_unit_horsepower_mechanical_label, "hp", 745.69987158227),
                ConvUnit(R.string.core_tools_unit_horsepower_metric_label, "PS", 735.49875),
            ),
        ),
        Category(
            "Pressure", R.string.core_tools_unit_category_pressure_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_pascal_label, "Pa", 1.0),
                ConvUnit(R.string.core_tools_unit_kilopascal_label, "kPa", 1000.0),
                ConvUnit(R.string.core_tools_unit_bar_label, "bar", 100_000.0),
                ConvUnit(R.string.core_tools_unit_atmosphere_label, "atm", 101_325.0),
                ConvUnit(R.string.core_tools_unit_psi_label, "psi", 6894.757293168),
                ConvUnit(R.string.core_tools_unit_mmhg_label, "mmHg", 133.322387415),
            ),
        ),
        Category(
            "Angle", R.string.core_tools_unit_category_angle_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_degree_label, "°", 1.0),
                ConvUnit(R.string.core_tools_unit_radian_label, "rad", 180.0 / Math.PI),
                ConvUnit(R.string.core_tools_unit_gradian_label, "gon", 0.9),
                ConvUnit(R.string.core_tools_unit_turn_label, "turn", 360.0),
                ConvUnit(R.string.core_tools_unit_arcminute_label, "′", 1.0 / 60.0),
                ConvUnit(R.string.core_tools_unit_arcsecond_label, "″", 1.0 / 3600.0),
            ),
        ),
        Category(
            "Frequency", R.string.core_tools_unit_category_frequency_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_hertz_label, "Hz", 1.0),
                ConvUnit(R.string.core_tools_unit_kilohertz_label, "kHz", 1e3),
                ConvUnit(R.string.core_tools_unit_megahertz_label, "MHz", 1e6),
                ConvUnit(R.string.core_tools_unit_gigahertz_label, "GHz", 1e9),
                ConvUnit(R.string.core_tools_unit_rpm_label, "rpm", 1.0 / 60.0),
            ),
        ),
        Category(
            "Fuel economy", R.string.core_tools_unit_category_fuel_economy_label,
            listOf(
                ConvUnit(R.string.core_tools_unit_litres_per_100_km_label, "L/100km", 1.0),
                ConvUnit(
                    R.string.core_tools_unit_kilometres_per_litre_label,
                    "km/L",
                    100.0,
                    reciprocal = true,
                ),
                ConvUnit(
                    R.string.core_tools_unit_miles_per_gallon_us_label,
                    "mpg",
                    235.214583,
                    reciprocal = true,
                ),
                ConvUnit(
                    R.string.core_tools_unit_miles_per_gallon_uk_label,
                    "mpg UK",
                    282.480936,
                    reciprocal = true,
                ),
            ),
        ),
    )
}
