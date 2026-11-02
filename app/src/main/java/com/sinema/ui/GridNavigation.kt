package com.sinema.ui

import com.sinema.model.SortOption
import java.text.NumberFormat
import java.util.Locale

internal object GridNavigation {
    fun positionLabel(
        firstVisible: Int,
        lastVisible: Int,
        totalCount: Int,
        locale: Locale = Locale.getDefault(),
        template: String = "%1\$s–%2\$s of %3\$s"
    ): String {
        if (firstVisible < 0 || lastVisible < firstVisible) return ""
        val formatter = NumberFormat.getIntegerInstance(locale)
        val first = formatter.format(firstVisible + 1)
        val last = formatter.format(lastVisible + 1)
        val total = formatter.format(maxOf(totalCount, lastVisible + 1))
        return String.format(locale, template, first, last, total)
    }

    fun alphaField(sort: SortOption): String = when (sort) {
        SortOption.TITLE_ASC, SortOption.TITLE_DESC -> "title"
        else -> "path"
    }
}
