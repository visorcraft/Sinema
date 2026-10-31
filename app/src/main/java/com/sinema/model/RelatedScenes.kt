package com.sinema.model

object RelatedScenes {
    fun rank(source: Scene, candidates: List<Scene>, limit: Int = 6): List<Scene> {
        if (limit <= 0) return emptyList()
        val performerIds = source.performers.mapTo(mutableSetOf()) { it.id }
        val tagIds = source.tags.mapTo(mutableSetOf()) { it.id }
        val studioId = source.studio?.id
        return candidates.asSequence()
            .distinctBy { it.id }
            .filter { it.id != source.id }
            .map { candidate ->
                val score = candidate.performers.count { it.id in performerIds } * 6 +
                    (if (studioId != null && candidate.studio?.id == studioId) 4 else 0) +
                    candidate.tags.count { it.id in tagIds } * 2
                candidate to score
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Scene, Int>> { it.second }
                    .thenByDescending { it.first.rating100 ?: -1 }
                    .thenBy { it.first.id }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }
}
