package com.searchlauncher.app.data

import com.searchlauncher.app.util.ContactUtils
import com.searchlauncher.app.util.FuzzyMatch

object SearchRanker {
  fun rankCandidates(
    query: String,
    snapshot: List<SearchableDocument>,
    includeSearchShortcuts: Boolean,
    usageStats: Map<String, Int>,
    queryUsageStats: Map<String, Int>,
    documentByNamespaceAndId: Map<String, SearchableDocument>,
  ): List<Pair<SearchableDocument, Int>> {
    val candidates = mutableListOf<Pair<SearchableDocument, Int>>()
    val queryLower = query.lowercase().trim()
    val usageQuery = normalizedUsageQuery(queryLower)
    val queryMask = calculateCharMask(queryLower)
    val normalizedQuery = ContactUtils.normalizePhoneNumber(queryLower)

    for (sdoc in snapshot) {
      if (!includeSearchShortcuts && sdoc.namespaceInt == 7) continue

      val fuzzyScore =
        FuzzyMatch.calculateScore(
          queryLower,
          sdoc.nameLower,
          sdoc.targetWords,
          sdoc.acronym,
          queryMask,
          sdoc.charMask,
        )

      val contactScore =
        if (
          sdoc.namespaceInt == 5 &&
            normalizedQuery != null &&
            normalizedQuery.length >= RankingScores.CONTACT_STRONG_MATCH_MIN_QUERY_LENGTH &&
            sdoc.normalizedPhone?.contains(normalizedQuery) == true
        ) {
          RankingScores.CONTACT_PHONE_MATCH_SCORE
        } else {
          0
        }

      val finalScore = maxOf(fuzzyScore, contactScore)
      if (finalScore > RankingScores.MIN_CANDIDATE_SCORE) {
        val doc = sdoc.doc
        val globalUsage = getGlobalUsageCount(usageStats, doc.namespace, doc.id)
        val queryUsagePoints =
          getQueryUsagePoints(queryUsageStats, usageQuery, doc.namespace, doc.id)
        val namespaceBoost = namespaceBoost(sdoc, queryLower, finalScore, queryUsagePoints)
        val usageBoost = usageBoost(globalUsage, queryUsagePoints)

        candidates.add(sdoc to (finalScore + namespaceBoost + usageBoost))
      }

      if (candidates.size > MAX_CANDIDATES) break
    }

    addLearnedShortQueryContacts(
      queryLower = queryLower,
      usageQuery = usageQuery,
      queryMask = queryMask,
      candidates = candidates,
      usageStats = usageStats,
      queryUsageStats = queryUsageStats,
      documentByNamespaceAndId = documentByNamespaceAndId,
    )

    return candidates.sortedByDescending { it.second }
  }

  fun calculateCharMask(text: String): Long {
    var mask = 0L
    for (c in text) {
      if (c in 'a'..'z') mask = mask or (1L shl (c - 'a'))
      else if (c in '0'..'9') mask = mask or (1L shl (c - '0' + 26))
    }
    return mask
  }

  private fun namespaceBoost(
    sdoc: SearchableDocument,
    queryLower: String,
    finalScore: Int,
    queryUsagePoints: Int,
  ): Int {
    var boost =
      when (sdoc.namespaceInt) {
        1 -> RankingScores.NAMESPACE_BOOST_APPS
        2 -> RankingScores.NAMESPACE_BOOST_APP_SHORTCUTS
        3 -> RankingScores.NAMESPACE_BOOST_WEB_BOOKMARKS
        4 -> RankingScores.NAMESPACE_BOOST_SHORTCUTS
        5 -> RankingScores.NAMESPACE_BOOST_CONTACTS
        8 -> RankingScores.NAMESPACE_BOOST_SNIPPETS
        else -> RankingScores.NAMESPACE_BOOST_DEFAULT
      }

    if (queryLower.length <= RankingScores.SHORT_QUERY_MAX_LENGTH && sdoc.namespaceInt <= 2) {
      boost += RankingScores.SHORT_QUERY_APP_BOOST
    }

    if (
      sdoc.namespaceInt == 5 &&
        queryLower.length >= RankingScores.CONTACT_STRONG_MATCH_MIN_QUERY_LENGTH &&
        finalScore >= RankingScores.CONTACT_PHONE_MATCH_SCORE
    ) {
      boost += RankingScores.CONTACT_STRONG_MATCH_BOOST
    }

    if (
      sdoc.namespaceInt == 5 &&
        queryLower.length <= RankingScores.LEARNED_CONTACT_SHORT_QUERY_MAX_LENGTH &&
        queryUsagePoints > 0 &&
        finalScore >= RankingScores.CONTACT_PHONE_MATCH_SCORE
    ) {
      boost += RankingScores.LEARNED_CONTACT_SHORT_QUERY_BOOST
    }

    return boost
  }

  private fun addLearnedShortQueryContacts(
    queryLower: String,
    usageQuery: String?,
    queryMask: Long,
    candidates: MutableList<Pair<SearchableDocument, Int>>,
    usageStats: Map<String, Int>,
    queryUsageStats: Map<String, Int>,
    documentByNamespaceAndId: Map<String, SearchableDocument>,
  ) {
    if (
      usageQuery == null || queryLower.length > RankingScores.LEARNED_CONTACT_SHORT_QUERY_MAX_LENGTH
    )
      return

    val existingContactIds =
      candidates.asSequence().filter { it.first.namespaceInt == 5 }.map { it.first.doc.id }.toSet()
    val keyPrefix = queryUsageKeyPrefix(usageQuery, "contacts")
    val learnedContactEntries =
      queryUsageStats.entries
        .asSequence()
        .filter { (key, points) -> points > 0 && key.startsWith(keyPrefix) }
        .sortedByDescending { it.value }
        .take(LEARNED_CONTACT_ENTRY_LIMIT)
        .toList()

    for ((key, queryUsagePoints) in learnedContactEntries) {
      val contactId = key.removePrefix(keyPrefix)
      if (contactId in existingContactIds) continue
      val sdoc = documentByNamespaceAndId[documentLookupKey("contacts", contactId)] ?: continue
      val finalScore =
        FuzzyMatch.calculateScore(
          queryLower,
          sdoc.nameLower,
          sdoc.targetWords,
          sdoc.acronym,
          queryMask,
          sdoc.charMask,
        )
      if (finalScore < RankingScores.CONTACT_PHONE_MATCH_SCORE) continue

      val doc = sdoc.doc
      val globalUsage = getGlobalUsageCount(usageStats, doc.namespace, doc.id)
      val usageBoost = usageBoost(globalUsage, queryUsagePoints)

      candidates.add(
        sdoc to
          (finalScore +
            RankingScores.NAMESPACE_BOOST_CONTACTS +
            RankingScores.LEARNED_CONTACT_SHORT_QUERY_BOOST +
            usageBoost)
      )
    }
  }

  private fun usageBoost(globalUsage: Int, queryUsagePoints: Int): Int =
    (globalUsage.coerceAtMost(RankingScores.GLOBAL_USAGE_SCORE_CAP) *
      RankingScores.GLOBAL_USAGE_SCORE_BOOST) +
      (queryUsagePoints.coerceAtMost(RankingScores.QUERY_USAGE_POINTS_CAP) *
        RankingScores.QUERY_USAGE_POINT_SCORE_BOOST)

  private fun getGlobalUsageCount(
    usageStats: Map<String, Int>,
    namespace: String,
    id: String,
  ): Int = usageStats[usageKey(namespace, id)] ?: usageStats[id] ?: 0

  private fun getQueryUsagePoints(
    queryUsageStats: Map<String, Int>,
    query: String?,
    namespace: String,
    id: String,
  ): Int = query?.let { queryUsageStats[queryUsageKey(it, namespace, id)] } ?: 0

  private fun normalizedUsageQuery(query: String?): String? =
    query?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

  private fun documentLookupKey(namespace: String, id: String): String =
    "$namespace$USAGE_KEY_SEPARATOR$id"

  private fun usageKey(namespace: String, id: String): String = "$namespace$USAGE_KEY_SEPARATOR$id"

  private fun queryUsageKeyPrefix(query: String, namespace: String): String =
    "$query$USAGE_KEY_SEPARATOR$namespace$USAGE_KEY_SEPARATOR"

  private fun queryUsageKey(query: String, namespace: String, id: String): String =
    "${queryUsageKeyPrefix(query, namespace)}$id"

  private const val USAGE_KEY_SEPARATOR = "\u001F"
  private const val MAX_CANDIDATES = 1000
  private const val LEARNED_CONTACT_ENTRY_LIMIT = 8
}
