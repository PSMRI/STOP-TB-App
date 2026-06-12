package org.piramalswasthya.stoptb.helpers

/**
 * Offline India Post DIGIPIN encoder.
 *
 * Translates a GPS coordinate pair into a 10-character hierarchical code
 * (displayed as XXXXX-XXXXX) based on the official 4×4 grid subdivision
 * over Indian territory.
 *
 * Reference bounds: Lat [2.5, 38.5]N  Lon [63.5, 99.5]E
 * Precision at level 10: ~34 metres.
 */
object DigiPinHelper {

    private val CHAR_MAP = arrayOf(
        charArrayOf('F', 'C', '9', '8'),
        charArrayOf('J', '3', '2', '7'),
        charArrayOf('K', '4', '5', '6'),
        charArrayOf('L', 'M', 'P', 'T')
    )

    private const val MIN_LAT = 2.5
    private const val MAX_LAT = 38.5
    private const val MIN_LON = 63.5
    private const val MAX_LON = 99.5

    /**
     * Generates a DIGIPIN for [lat]/[lon].
     * Returns `null` if the coordinate is outside the supported India bounds.
     */
    fun generate(lat: Double, lon: Double): String? {
        if (lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) return null

        var minLat = MIN_LAT
        var maxLat = MAX_LAT
        var minLon = MIN_LON
        var maxLon = MAX_LON

        val sb = StringBuilder(10)

        repeat(10) {
            val latDiv = (maxLat - minLat) / 4.0
            val lonDiv = (maxLon - minLon) / 4.0

            // Rows go north→south (row 0 = northernmost band)
            val row = ((maxLat - lat) / latDiv).toInt().coerceIn(0, 3)
            // Cols go west→east (col 0 = westernmost band)
            val col = ((lon - minLon) / lonDiv).toInt().coerceIn(0, 3)

            sb.append(CHAR_MAP[row][col])

            maxLat -= row * latDiv
            minLat = maxLat - latDiv
            minLon += col * lonDiv
            maxLon = minLon + lonDiv
        }

        sb.insert(5, '-')
        return sb.toString()
    }

    /** Returns true when the coordinate falls inside the supported India bounds. */
    fun isWithinIndia(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
}
