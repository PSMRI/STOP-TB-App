package org.piramalswasthya.stoptb.configuration

import android.text.InputFilter
import android.text.Spanned

/** Allows Unicode letters, combining marks, and spaces. Digits and symbols are dropped. */
object AlphabetInputFilter : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val input = source.subSequence(start, end).toString()
        val filtered = input.filter { it.isAlphabetOrSpace() }
        return if (filtered == input) null else filtered
    }
}

internal fun Char.isAlphabetOrSpace(): Boolean {
    if (isLetter() || isWhitespace()) return true
    val type = Character.getType(this)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}
