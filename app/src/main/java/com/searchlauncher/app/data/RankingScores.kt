package com.searchlauncher.app.data

/**
 * Centralized ranking weights for search results.
 *
 * Final ordering is by [SearchResult.rankingScore] descending. Two paths feed it:
 * 1. Direct scores - smart actions, custom shortcuts, suggestions, widgets: a literal value chosen
 *    so the result lands at the desired position.
 * 2. Indexed scores - apps, app_shortcuts, snippets, shortcuts, contacts, web_saved, web_bookmarks:
 *    FuzzyMatch.calculateScore (0..100) + a namespace boost + optional context boost + usage.
 *
 * Approximate descending order of typical scores: 1600 timer smart action; 1200 custom shortcut
 * with explicit search term ("g cats"); ~450-480 a page open in the browser; ~150-250 indexed hits,
 * which usage history lifts to ~525 at most (varies by namespace, short-query boost, usage); 200
 * suggestion / widget result; 150 custom shortcut bare alias; 100 call / email smart action; 98-99
 * sms / url / add-contact smart action
 *
 * Learning is deliberately kept within one order of magnitude of the structural signals, so that
 * having picked something once at one exact query nudges the order rather than dictating it.
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

  /**
   * Base score for a page open in the browser right now, with the FuzzyMatch score added on top.
   *
   * A direct score rather than a namespace boost, because "this page is open" says something about
   * what the user is working with rather than about how well the text matched. On the indexed scale
   * it lost constantly: anything with usage history behind it beat an open tab however well the tab
   * matched.
   *
   * Tracks the usage ceiling, which is what it has to clear to mean anything: an open tab lands
   * around 450-480, above an ordinary indexed hit (~250) and a partly-learned one, but below a
   * result the user has picked at this exact query before (~525). Lowering [QUERY_USAGE_SCORE_MAX]
   * without lowering this in step would make an open tab unbeatable.
   *
   * Sits below [CUSTOM_SHORTCUT_WITH_SEARCH_TERM] so that typing an explicit shortcut search still
   * wins — "g cats" means search, whatever happens to be open.
   */
  const val BROWSER_TAB_BASE = 380

  /**
   * Tabs must genuinely contain the query — FuzzyMatch's "contains" grade — rather than merely
   * fuzzy-match it. With a base score this high, a loose subsequence hit would bury exact matches
   * from everything else.
   */
  const val BROWSER_TAB_MIN_SCORE = 70

  // --- Index pipeline: namespace boost added to the FuzzyMatch score ---
  const val NAMESPACE_BOOST_APPS = 150
  const val NAMESPACE_BOOST_APP_SHORTCUTS = 130
  const val NAMESPACE_BOOST_SNIPPETS = 100
  /** Explicitly saved bookmarks outrank passively-recorded history. */
  const val NAMESPACE_BOOST_WEB_SAVED = 110
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

  // --- Usage-based boost: globalUsage * GLOBAL_BOOST + scaled query-usage points ---
  const val GLOBAL_USAGE_SCORE_BOOST = 5
  const val GLOBAL_USAGE_SCORE_CAP = 5

  /**
   * Points stored per query→result association. Picking a result records [QUERY_USAGE_POINTS_CAP]
   * against the exact query typed, and a fraction of it against each prefix of that query.
   */
  const val QUERY_USAGE_POINTS_CAP = 500

  /**
   * Score a fully-learned query→result association is worth, with fewer points scaled down
   * proportionally.
   *
   * Deliberately the same order of magnitude as the namespace boosts (80-150) and the FuzzyMatch
   * scale (0-100) rather than several times larger. It used to be worth 1000, which meant a single
   * pick at one exact query outweighed every structural signal combined: typing "tesla" put a
   * history entry above the Tesla app, because the app had only ever been picked at "tesl" and so
   * had learned nothing about the longer string. Learning should reorder near-equals, not overrule
   * an exact name match.
   */
  const val QUERY_USAGE_SCORE_MAX = 250

  // --- Filtering ---
  /** Documents whose finalScore is at or below this threshold are dropped before ranking. */
  const val MIN_CANDIDATE_SCORE = 30
}
