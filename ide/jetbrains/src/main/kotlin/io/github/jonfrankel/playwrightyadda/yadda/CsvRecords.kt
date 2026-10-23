package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.util.TextRange

/**
 * The records of a CSV or TSV block, as RFC 4180 reads them: a field in double quotes may hold the
 * delimiter and line breaks, and `""` inside one is a quote. Blank lines are not records.
 */
object CsvRecords {
  class Record(val range: TextRange, val fields: Int)

  class Result(val records: List<Record>, val unterminatedQuoteAt: Int?)

  fun parse(text: String, delimiter: Char = ','): Result {
    val records = mutableListOf<Record>()
    var start = -1
    var fields = 1
    var inQuotes = false
    var quoteAt = -1
    var atFieldStart = true
    var i = 0

    fun finish(end: Int) {
      if (start >= 0) records += Record(TextRange(start, end), fields)
      start = -1
      fields = 1
      atFieldStart = true
    }

    while (i < text.length) {
      val c = text[i]
      when {
        inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> i++
        inQuotes && c == '"' -> inQuotes = false
        inQuotes -> {}
        c == '\n' -> finish(i)
        c == '\r' || ((c == ' ' || c == '\t') && c != delimiter) -> {}
        else -> {
          if (start < 0) start = i
          when {
            c == delimiter -> {
              fields++
              atFieldStart = true
            }
            c == '"' && atFieldStart -> {
              inQuotes = true
              quoteAt = i
              atFieldStart = false
            }
            else -> atFieldStart = false
          }
        }
      }
      i++
    }
    if (inQuotes) return Result(records, quoteAt)
    finish(text.length)
    return Result(records, null)
  }
}
