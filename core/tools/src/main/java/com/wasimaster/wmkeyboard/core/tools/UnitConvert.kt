package com.wasimaster.wmkeyboard.core.tools

/**
 * Unit-conversion catalog and math for the converter tool. Each unit maps
 * to its category's base unit as `base = value * factor + offset`
 * (offset only for temperatures); [ConvUnit.reciprocal] marks fuel-economy
 * style units where `base = factor / value`. All local, no network.
 */
object UnitConvert {

    data class ConvUnit(
        val name: String,
        val symbol: String,
        val factor: Double,
        val offset: Double = 0.0,
        val reciprocal: Boolean = false,
    )

    data class Category(val name: String, val units: List<ConvUnit>)

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
            "Length",
            listOf(
                ConvUnit("Millimetre", "mm", 0.001),
                ConvUnit("Centimetre", "cm", 0.01),
                ConvUnit("Metre", "m", 1.0),
                ConvUnit("Kilometre", "km", 1000.0),
                ConvUnit("Inch", "in", 0.0254),
                ConvUnit("Foot", "ft", 0.3048),
                ConvUnit("Yard", "yd", 0.9144),
                ConvUnit("Mile", "mi", 1609.344),
                ConvUnit("Nautical mile", "nmi", 1852.0),
                ConvUnit("Micrometre", "µm", 1e-6),
                ConvUnit("Nanometre", "nm", 1e-9),
                ConvUnit("Light-year", "ly", 9.4607304725808e15),
            ),
        ),
        Category(
            "Mass",
            listOf(
                ConvUnit("Milligram", "mg", 1e-6),
                ConvUnit("Gram", "g", 0.001),
                ConvUnit("Kilogram", "kg", 1.0),
                ConvUnit("Tonne", "t", 1000.0),
                ConvUnit("Ounce", "oz", 0.028349523125),
                ConvUnit("Pound", "lb", 0.45359237),
                ConvUnit("Stone", "st", 6.35029318),
                ConvUnit("US ton", "ton", 907.18474),
                ConvUnit("Carat", "ct", 0.0002),
            ),
        ),
        Category(
            "Temperature",
            listOf(
                ConvUnit("Celsius", "°C", 1.0, 273.15),
                ConvUnit("Fahrenheit", "°F", 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0),
                ConvUnit("Kelvin", "K", 1.0),
            ),
        ),
        Category(
            "Area",
            listOf(
                ConvUnit("Square millimetre", "mm²", 1e-6),
                ConvUnit("Square centimetre", "cm²", 1e-4),
                ConvUnit("Square metre", "m²", 1.0),
                ConvUnit("Hectare", "ha", 10_000.0),
                ConvUnit("Square kilometre", "km²", 1e6),
                ConvUnit("Square inch", "in²", 0.00064516),
                ConvUnit("Square foot", "ft²", 0.09290304),
                ConvUnit("Square yard", "yd²", 0.83612736),
                ConvUnit("Acre", "ac", 4046.8564224),
                ConvUnit("Square mile", "mi²", 2_589_988.110336),
                ConvUnit("Katha (BD)", "katha", 66.8902),
                ConvUnit("Bigha (BD)", "bigha", 1337.803),
            ),
        ),
        Category(
            "Volume",
            listOf(
                ConvUnit("Millilitre", "mL", 0.001),
                ConvUnit("Litre", "L", 1.0),
                ConvUnit("Cubic metre", "m³", 1000.0),
                ConvUnit("Teaspoon (US)", "tsp", 0.00492892159375),
                ConvUnit("Tablespoon (US)", "tbsp", 0.01478676478125),
                ConvUnit("Fluid ounce (US)", "fl oz", 0.0295735295625),
                ConvUnit("Cup (US)", "cup", 0.2365882365),
                ConvUnit("Pint (US)", "pt", 0.473176473),
                ConvUnit("Quart (US)", "qt", 0.946352946),
                ConvUnit("Gallon (US)", "gal", 3.785411784),
                ConvUnit("Gallon (UK)", "gal UK", 4.54609),
                ConvUnit("Cubic inch", "in³", 0.016387064),
                ConvUnit("Cubic foot", "ft³", 28.316846592),
            ),
        ),
        Category(
            "Speed",
            listOf(
                ConvUnit("Metres/second", "m/s", 1.0),
                ConvUnit("Kilometres/hour", "km/h", 1 / 3.6),
                ConvUnit("Miles/hour", "mph", 0.44704),
                ConvUnit("Knot", "kn", 1852.0 / 3600.0),
                ConvUnit("Feet/second", "ft/s", 0.3048),
                ConvUnit("Mach (sea level)", "Mach", 340.29),
            ),
        ),
        Category(
            "Time",
            listOf(
                ConvUnit("Millisecond", "ms", 0.001),
                ConvUnit("Second", "s", 1.0),
                ConvUnit("Minute", "min", 60.0),
                ConvUnit("Hour", "h", 3600.0),
                ConvUnit("Day", "d", 86_400.0),
                ConvUnit("Week", "wk", 604_800.0),
                ConvUnit("Month (avg)", "mo", 2_629_746.0),
                ConvUnit("Year", "yr", 31_556_952.0),
            ),
        ),
        Category(
            "Data",
            listOf(
                ConvUnit("Bit", "bit", 0.125),
                ConvUnit("Byte", "B", 1.0),
                ConvUnit("Kilobyte", "kB", 1e3),
                ConvUnit("Megabyte", "MB", 1e6),
                ConvUnit("Gigabyte", "GB", 1e9),
                ConvUnit("Terabyte", "TB", 1e12),
                ConvUnit("Kibibyte", "KiB", 1024.0),
                ConvUnit("Mebibyte", "MiB", 1_048_576.0),
                ConvUnit("Gibibyte", "GiB", 1_073_741_824.0),
                ConvUnit("Tebibyte", "TiB", 1_099_511_627_776.0),
            ),
        ),
        Category(
            "Energy",
            listOf(
                ConvUnit("Joule", "J", 1.0),
                ConvUnit("Kilojoule", "kJ", 1000.0),
                ConvUnit("Calorie", "cal", 4.184),
                ConvUnit("Kilocalorie", "kcal", 4184.0),
                ConvUnit("Watt-hour", "Wh", 3600.0),
                ConvUnit("Kilowatt-hour", "kWh", 3_600_000.0),
                ConvUnit("BTU", "BTU", 1055.05585262),
                ConvUnit("Electronvolt", "eV", 1.602176634e-19),
            ),
        ),
        Category(
            "Power",
            listOf(
                ConvUnit("Watt", "W", 1.0),
                ConvUnit("Kilowatt", "kW", 1000.0),
                ConvUnit("Megawatt", "MW", 1e6),
                ConvUnit("Horsepower (mech)", "hp", 745.69987158227),
                ConvUnit("Horsepower (metric)", "PS", 735.49875),
            ),
        ),
        Category(
            "Pressure",
            listOf(
                ConvUnit("Pascal", "Pa", 1.0),
                ConvUnit("Kilopascal", "kPa", 1000.0),
                ConvUnit("Bar", "bar", 100_000.0),
                ConvUnit("Atmosphere", "atm", 101_325.0),
                ConvUnit("PSI", "psi", 6894.757293168),
                ConvUnit("mmHg (Torr)", "mmHg", 133.322387415),
            ),
        ),
        Category(
            "Angle",
            listOf(
                ConvUnit("Degree", "°", 1.0),
                ConvUnit("Radian", "rad", 180.0 / Math.PI),
                ConvUnit("Gradian", "gon", 0.9),
                ConvUnit("Turn", "turn", 360.0),
                ConvUnit("Arcminute", "′", 1.0 / 60.0),
                ConvUnit("Arcsecond", "″", 1.0 / 3600.0),
            ),
        ),
        Category(
            "Frequency",
            listOf(
                ConvUnit("Hertz", "Hz", 1.0),
                ConvUnit("Kilohertz", "kHz", 1e3),
                ConvUnit("Megahertz", "MHz", 1e6),
                ConvUnit("Gigahertz", "GHz", 1e9),
                ConvUnit("RPM", "rpm", 1.0 / 60.0),
            ),
        ),
        Category(
            "Fuel economy",
            listOf(
                ConvUnit("Litres/100 km", "L/100km", 1.0),
                ConvUnit("Kilometres/litre", "km/L", 100.0, reciprocal = true),
                ConvUnit("Miles/gallon (US)", "mpg", 235.214583, reciprocal = true),
                ConvUnit("Miles/gallon (UK)", "mpg UK", 282.480936, reciprocal = true),
            ),
        ),
    )
}
