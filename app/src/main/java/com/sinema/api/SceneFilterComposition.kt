package com.sinema.api

internal fun combineSceneFilters(
    base: Map<String, Any?>,
    additional: Map<String, Any?>
): Map<String, Any?> {
    if (base.isEmpty()) return additional
    if (additional.isEmpty()) return base
    if (base.keys.intersect(additional.keys).isEmpty()) return base + additional
    if (additional.containsKey("AND")) {
        return (flattenAnd(base) + flattenAnd(additional)).reduceRight { part, nested ->
            part + mapOf("AND" to nested)
        }
    }
    return mapOf("AND" to (mapOf("AND" to base) + additional))
}

private fun flattenAnd(filter: Map<String, Any?>): List<Map<String, Any?>> {
    val current = filter - "AND"
    val nested = filter["AND"] as? Map<*, *>
    @Suppress("UNCHECKED_CAST")
    val children = nested?.let { flattenAnd(it as Map<String, Any?>) }.orEmpty()
    return listOf(current) + children
}
