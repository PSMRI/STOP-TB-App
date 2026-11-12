package org.piramalswasthya.stoptb.configuration

import android.text.InputFilter
import android.text.Spanned

/** Allows Unicode letters, digits, combining marks and spaces. */
object AlphanumericInputFilter : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {

        val input = source.subSequence(start, end).toString()
        val filtered = input.filter {
            it.isLetterOrDigit() ||
                    it == ' ' ||
                    Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() ||
                    Character.getType(it) == Character.ENCLOSING_MARK.toInt()
        }

        return if (filtered == input) null else filtered
    }
}