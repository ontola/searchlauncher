package com.searchlauncher.app.data

/**
 * Centralized ranking weights for search results.
 *
 * Final ordering is by [SearchResult.rankingScore] descending. Two paths feed it:
 * 1. Direct scores - smart actions, custom shortcuts, suggestions, widgets: a literal value chosen
 *    so the result lands at the desired position.
 * 2. Matched scores - apps, app_shortcuts, snippets, shortcuts, contacts, calendar, downloads,
 *    web_saved, web_bookmarks, private-space apps and open browser tabs: [matchScore] (how well the
 *    text matched, 0..1000) + a type boost + optional context boost + usage.
 *
 * How well the name matches is the primary signal. FuzzyMatch grades a match on a 0..100 scale
 * (exact=100, prefix=90, word=85, acronym=80, contains=70, typo=58-68, subsequence=10-60) and
 * [matchScore] multiplies that by [MATCH_WEIGHT], so one grade step is worth at least [GRADE_STEP].
 * Everything structural - what kind of result it is, whether it is open right now, how often it has
 * been opened - stays below that step, so a name that *starts* with the query beats a name in which
 * some later word starts with it, whatever the two are. Type and context only decide between
 * results of the same grade. Learning (a result picked before at this query) is the one thing
 * allowed to cross grades, and it crosses at most a few.
 *
 * Approximate descending order of typical scores: 2500 timer smart action; 2000 custom shortcut
 * with explicit search term ("g cats"); 1000-1040 exact name match; 900-950 name starts with the
 * query (an open tab at the top of that band, apps next, contacts last); 850-900 a word in the name
 * starts with it; 700-750 the name merely contains it; 650 suggestion / widget result; 600 custom
 * shortcut bare alias; 500 call / email smart action; 498-499 sms / url / add-contact smart action;
 * 100-600 scattered-character matches. A learned pick adds up to [QUERY_USAGE_SCORE_MAX] on top.
 */
object RankingScores {
  // --- Match quality: the FuzzyMatch grade scaled up to be the primary signal ---
  /** FuzzyMatch's 0..100 grade times this is the match component of a matched score. */
  const val MATCH_WEIGHT = 10

  /**
   * The smallest distance between two FuzzyMatch grades that mean different things (prefix 90 vs
   * word 85), after weighting. Type and context boosts, including their global-usage top-up, stay
   * below this so a better match wins over a better type.
   */
  const val GRADE_STEP = 5 * MATCH_WEIGHT

  /** The match component of a matched score, from a FuzzyMatch grade. */
  fun matchScore(fuzzyScore: Int): Int = fuzzyScore * MATCH_WEIGHT

  // --- Smart actions (deterministic pattern matches on the whole query) ---
  const val SMART_ACTION_TIMER = 2500
  const val SMART_ACTION_CALL = 500
  const val SMART_ACTION_EMAIL = 500
  const val SMART_ACTION_SMS = 499
  const val SMART_ACTION_URL = 498
  const val SMART_ACTION_ADD_CONTACT = 498

  // --- Direct scores outside the match pipeline ---
  /** Above anything a matched score can reach, learning and open tabs included. */
  const val CUSTOM_SHORTCUT_WITH_SEARCH_TERM = 2000
  /** Below anything whose name contains the query; above scattered-character matches. */
  const val CUSTOM_SHORTCUT_TRIGGER_ONLY = 600
  const val SUGGESTION = 650
  const val WIDGET_RESULT = 650

  /**
   * Boost for a page open in the browser right now, added to its [matchScore].
   *
   * "This page is open" says something about what the user is working with, so at the same match
   * grade an open tab beats every indexed type, including one with global usage behind it (at most
   * [NAMESPACE_BOOST_APPS] + [GLOBAL_USAGE_SCORE_MAX], or the strong-match contact equivalent). It
   * stays below [GRADE_STEP] + [NAMESPACE_BOOST_CONTACTS], so a tab that merely has a word starting
   * with the query never outranks a contact, app or anything else whose name starts with it. Typing
   * "i" must show what starts with an i, not a tab with "issues" in its title. A result the user
   * has picked at this query before still wins, as its learned boost is several times larger.
   */
  const val BROWSER_TAB_BOOST = 50

  /**
   * Tabs must genuinely contain the query — FuzzyMatch's "contains" grade — rather than merely
   * fuzzy-match it. A tab is a strong contextual hint, and a loose subsequence hit would still show
   * it above the scattered-character matches from the index.
   */
  const val BROWSER_TAB_MIN_SCORE = 70

  // --- Match pipeline: type boost added to the match score, all below GRADE_STEP ---
  const val NAMESPACE_BOOST_APPS = 35
  const val NAMESPACE_BOOST_APP_SHORTCUTS = 30
  /** Explicitly saved bookmarks outrank passively-recorded history. */
  const val NAMESPACE_BOOST_WEB_SAVED = 26
  const val NAMESPACE_BOOST_SNIPPETS = 22
  const val NAMESPACE_BOOST_CALENDAR = 20
  const val NAMESPACE_BOOST_WEB_BOOKMARKS = 16
  const val NAMESPACE_BOOST_SHORTCUTS = 12
  const val NAMESPACE_BOOST_DOWNLOADS = 8
  const val NAMESPACE_BOOST_CONTACTS = 5
  const val NAMESPACE_BOOST_DEFAULT = 0

  // --- Context boosts stacked on top of the type boost ---
  /**
   * Score assigned to a contact whose normalized phone number contains the query. Also used as the
   * threshold for the strong-match contact boost below.
   */
  const val CONTACT_PHONE_MATCH_SCORE = 85

  /**
   * Boost for contacts on 3+ char queries when they pass the strong-match threshold. Lifts them
   * just above apps of the same grade: three letters into a name is more likely a person than an
   * app, while one or two letters is still mostly app launching.
   */
  const val CONTACT_STRONG_MATCH_MIN_QUERY_LENGTH = 3
  const val CONTACT_STRONG_MATCH_BOOST = 32

  /**
   * Boost for contacts the user has launched before with a 1-2 char query. Worth two grade steps,
   * so a contact picked at "polle" sits above apps that start with "p", but below an app the user
   * has picked at "p" itself (which learns up to [QUERY_USAGE_SCORE_MAX]).
   */
  const val LEARNED_CONTACT_SHORT_QUERY_MAX_LENGTH = 2
  const val LEARNED_CONTACT_SHORT_QUERY_BOOST = 100

  // --- Usage-based boost: globalUsage * GLOBAL_BOOST + scaled query-usage points ---
  /**
   * Global usage is deliberately weak: a tie-breaker between results of the same grade and type,
   * never a reason to outrank a better match or a type placed above it.
   */
  const val GLOBAL_USAGE_SCORE_BOOST = 2
  const val GLOBAL_USAGE_SCORE_CAP = 5
  const val GLOBAL_USAGE_SCORE_MAX = GLOBAL_USAGE_SCORE_BOOST * GLOBAL_USAGE_SCORE_CAP

  /**
   * Points stored per query→result association. Picking a result records [QUERY_USAGE_POINTS_CAP]
   * against the exact query typed, and a fraction of it against each prefix of that query.
   */
  const val QUERY_USAGE_POINTS_CAP = 500

  /**
   * Score a fully-learned query→result association is worth, with fewer points scaled down
   * proportionally.
   *
   * Worth a few grade steps: a result the user keeps picking at this query climbs from "contains"
   * past an unlearned "starts with", but not past an exact name match, and a pick at a longer query
   * only nudges the shorter prefixes. It used to be several times the whole match scale, which
   * meant a single pick at one exact query outweighed every structural signal combined. Learning
   * should reorder near-equals, not overrule how well the name matches.
   */
  const val QUERY_USAGE_SCORE_MAX = 250

  // --- Filtering ---
  /** Documents whose FuzzyMatch grade is at or below this threshold are dropped before ranking. */
  const val MIN_CANDIDATE_SCORE = 30
}
