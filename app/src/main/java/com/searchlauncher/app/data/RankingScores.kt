package com.searchlauncher.app.data

/**
 * Centralized ranking weights for search results.
 *
 * Final ordering is by [SearchResult.rankingScore] descending. Two paths feed it:
 * 1. Direct scores - smart actions, custom shortcuts, suggestions, widgets: a literal value chosen
 *    so the result lands at the desired position.
 * 2. Indexed scores - apps, app_shortcuts, snippets, shortcuts, contacts, web_bookmarks:
 *    FuzzyMatch.calculateScore (0..100) + a namespace boost + optional context boost + usage.
 *
 * Approximate descending order of typical scores: 1200 custom shortcut with explicit search term
 * ("g cats") 1600 timer smart action ~200-440 indexed hits (varies by namespace, short-query boost,
 * usage) 200 suggestion / widget result 150 custom shortcut bare alias 100 call / email smart
 * action 98-99 sms / url / add-contact smart action
 *
 * FuzzyMatch's internal 0..100 scale (exact=100, prefix=90, word=85, acronym=80, contains=70,
 * typo=58-68, subsequence=10-60) lives in FuzzyMatch.kt - it's the match-quality signal that gets
 * *added* to the namespace boosts below.
 */
object RankingScores {
  // --- Smart actions (deterministic pattern matches on the whole query) ---
  const val SMART_ACTION_TIMER = 1600
  const val SMART_ACTION_CALL = 100
  const val SMART_ACTION_EMAIL = 100
  const val SMART_ACTION_SMS = 99
  const val SMART_ACTION_URL = 98
  const val SMART_ACTION_ADD_CONTACT = 98

  // --- Direct scores outside the index pipeline ---
  const val CUSTOM_SHORTCUT_WITH_SEARCH_TERM = 1200
  const val CUSTOM_SHORTCUT_TRIGGER_ONLY = 150
  const val SUGGESTION = 200
  const val WIDGET_RESULT = 200

  // --- Index pipeline: namespace boost added to the FuzzyMatch score ---
  const val NAMESPACE_BOOST_APPS = 150
  const val NAMESPACE_BOOST_APP_SHORTCUTS = 130
  const val NAMESPACE_BOOST_SNIPPETS = 100
  const val NAMESPACE_BOOST_WEB_BOOKMARKS = 80
  const val NAMESPACE_BOOST_SHORTCUTS = 70
  const val NAMESPACE_BOOST_CONTACTS = 40
  const val NAMESPACE_BOOST_DEFAULT = 0

  // --- Context boosts stacked on top of the namespace boost ---
  /** Extra boost for apps / app_shortcuts when the user typed a very short query. */
  const val SHORT_QUERY_APP_BOOST = 200
  const val SHORT_QUERY_MAX_LENGTH = 2

  /**
   * Score assigned to a contact whose normalized phone number contains the query. Also used as the
   * threshold for the strong-match contact boost below.
   */
  const val CONTACT_PHONE_MATCH_SCORE = 85

  /** Boost for contacts on 3+ char queries when they pass the strong-match threshold. */
  const val CONTACT_STRONG_MATCH_MIN_QUERY_LENGTH = 3
  const val CONTACT_STRONG_MATCH_BOOST = 120

  /** Boost for contacts the user has launched before with a 1-2 char query. */
  const val LEARNED_CONTACT_SHORT_QUERY_MAX_LENGTH = 2
  const val LEARNED_CONTACT_SHORT_QUERY_BOOST = 320

  // --- Usage-based boost: globalUsage * GLOBAL_BOOST + queryUsagePoints * QUERY_BOOST ---
  const val GLOBAL_USAGE_SCORE_BOOST = 5
  const val GLOBAL_USAGE_SCORE_CAP = 5
  const val QUERY_USAGE_POINT_SCORE_BOOST = 2
  const val QUERY_USAGE_POINTS_CAP = 500

  // --- Filtering ---
  /** Documents whose finalScore is at or below this threshold are dropped before ranking. */
  const val MIN_CANDIDATE_SCORE = 30
}
