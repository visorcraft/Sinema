package com.sinema.ui

internal class GridPageState(private val paged: Boolean) {
    private var page = 0
    private var totalCount = 0
    private var loadedCount = 0
    private var lastBatchEmpty = true

    val canLoadMore: Boolean
        get() = paged && page > 0 && !lastBatchEmpty && loadedCount < totalCount

    val nextPage: Int get() = page + 1

    fun reset() {
        page = 0
        totalCount = 0
        loadedCount = 0
        lastBatchEmpty = true
    }

    fun record(totalCount: Int, batchSize: Int, loadedCount: Int) {
        page++
        this.totalCount = totalCount
        this.loadedCount = loadedCount
        lastBatchEmpty = batchSize == 0
    }
}
