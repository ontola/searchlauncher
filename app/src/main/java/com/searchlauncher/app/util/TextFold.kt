package com.searchlauncher.app.util

import java.text.Normalizer

/**
 * Folds accented Latin letters to their plain form so "tel" finds "Téléphone" and "tél" finds
 * "Telegram". Input is expected to be lowercased already.
 *
 * Only the combining diacritical marks block (U+0300..U+036F) is stripped, and the text is
 * recomposed afterwards. That keeps scripts whose marks change the letter itself (Japanese dakuten,
 * Devanagari vowel signs, Hangul syllables) exactly as they were.
 */
object TextFold {
  fun fold(text: String): String {
    if (text.all { it.code < 0x80 }) return text
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for (c in decomposed) {
      when (c) {
        in '̀'..'ͯ' -> Unit
        'ß' -> out.append("ss")
        'æ' -> out.append("ae")
        'œ' -> out.append("oe")
        'ø' -> out.append('o')
        'ł' -> out.append('l')
        'đ' -> out.append('d')
        'ð' -> out.append('d')
        'þ' -> out.append("th")
        'ı' -> out.append('i')
        'ĳ' -> out.append("ij")
        else -> out.append(c)
      }
    }
    return Normalizer.normalize(out, Normalizer.Form.NFC)
  }

  /** Case- and accent-insensitive [String.contains]. */
  fun containsFolded(text: String, query: String): Boolean =
    fold(text.lowercase()).contains(fold(query.lowercase()))
}
