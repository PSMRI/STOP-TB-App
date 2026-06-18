package org.piramalswasthya.stoptb.helpers

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

            val row = ((maxLat - lat) / latDiv).toInt().coerceIn(0, 3)
            val col = ((lon - minLon) / lonDiv).toInt().coerceIn(0, 3)

            sb.append(CHAR_MAP[row][col])

            maxLat -= row * latDiv
            minLat = maxLat - latDiv
            minLon += col * lonDiv
            maxLon = minLon + lonDiv
        }

        return sb.toString()
    }

    fun isWithinIndia(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
}
