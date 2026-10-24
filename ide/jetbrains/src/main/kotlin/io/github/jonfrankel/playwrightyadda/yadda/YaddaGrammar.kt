package io.github.jonfrankel.playwrightyadda.yadda

/**
 * Yadda's feature parsers, English, ported line for line from `yadda/lib/parsers` (3.2): the same
 * line classes tried in the same order, and the same constructs, each with the events it accepts
 * at each point. It exists to say what Yadda would make of a file, without running Node: where
 * it throws, what a multi-line step's text comes out as, which example row a step is expanded for.
 *
 * There are two surface syntaxes, [analyze] for `.feature` files and [analyzeMarkdown] for GitHub-flavoured
 * Markdown. They differ only in how a line becomes an event; the constructs the events build are the same.
 *
 * Yadda stops at its first error, and so does this. `YaddaGrammarTest` holds it to answers
 * generated from the real parser (`generate-parser-cases.mjs`), so a change in Yadda shows up there.
 */
object YaddaGrammar {
  /**
   * Where Yadda throws. [line] is the line to flag, 1-based: for a construct that turns out to be
   * incomplete, its own heading. [reported] is the line in Yadda's "Error parsing line N", which is
   * the line that made it notice, and absent for what it finds only once the file has ended.
   */
  class Issue(val line: Int, val message: String, val reported: Int?)

  /** A step as Yadda sees it: its first line's number, and its text with any fenced block after a `\n`. */
  class Step(val line: Int, var text: String, private val scope: Scope, val column: Int = 0) {
    /** The block under the first line, without the fences; null for a step of one line. */
    val block: String? get() = if ('\n' in text) text.substringAfter('\n') else null

    /** The steps of the scenario (or background) this one is in, in order, this one included. */
    val siblings: List<Step> get() = scope.steps

    /** [name] with the placeholders of the scenario's first examples row filled in, as Yadda does for each row. */
    fun expanded(name: String): String = scope.first?.let { substitute(it, name) } ?: name
  }

  /** The examples of the scenario a step belongs to, shared by its steps. */
  class Scope(val title: String = "") {
    var first: Map<String, List<String>>? = null
    val steps = mutableListOf<Step>()
  }

  enum class MarkerKind { ANNOTATION, FEATURE, RULE, SCENARIO, BACKGROUND, EXAMPLES }

  /**
   * A line that starts a construct, or an annotation, as Yadda reads it. [title] is what follows the
   * keyword, [key] and [value] are an annotation's (a bare `@tag` has no value), and [column] is where
   * the keyword (or the `@`) starts in its line. All are kept even for what comes after the first error.
   */
  class Marker(val kind: MarkerKind, val line: Int, val title: String, val key: String, val value: String?, val column: Int)

  /** The columns of one `Examples:` table, with the scenario they fill in. */
  class ExamplesInfo(val line: Int, val headerLine: Int, val columns: List<String>, val scenario: Scope)

  class Result(
    /** The first thing Yadda would throw about, if anything. */
    val issue: Issue?,
    /** The lines of `Rule:`, which Yadda parses but playwright-yadda doesn't run. */
    val ruleLines: List<Int>,
    /** Every step Yadda read up to the issue, by the line of its first line. */
    val steps: Map<Int, Step>,
    /** Every heading and annotation in the file, even past the issue. */
    val markers: List<Marker> = emptyList(),
    /** The examples tables read up to the issue. */
    val examples: List<ExamplesInfo> = emptyList(),
  )

  private val MULTI_LINE_COMMENT = Regex("""^\s*#{3,}""")
  private val SINGLE_LINE_COMMENT = Regex("""^\s*#""")
  private val BLANK = Regex("""^(\s*)$""")
  private val DASH = Regex("""(^\s*[|┆]?-{3,})""")
  private val SIMPLE_ANNOTATION = Regex("""^\s*@([^=]*)$""")
  private val NVP_ANNOTATION = Regex("""^\s*@([^=]*)=(.*)$""")
  private val FEATURE = keyword("[Ff]eature", heading = true)
  private val RULE = keyword("[Rr]ule", heading = true)
  private val SCENARIO = keyword("(?:[Ss]cenario|[Ss]cenario [Oo]utline)", heading = true)
  private val BACKGROUND = keyword("[Bb]ackground", heading = true)
  private val EXAMPLES = keyword("(?:[Ee]xamples|[Ww]here)", heading = false)

  private fun keyword(word: String, heading: Boolean) =
    Regex("""^\s*$word:\s*${if (heading) "(.*)" else ""}""", RegexOption.IGNORE_CASE)

  /** What Yadda makes of a `.feature` file. */
  fun analyze(text: String): Result {
    val events = mutableListOf<Event>()
    var comment = false
    for ((index, line) in text.split("\r\n", "\n").withIndex()) {
      if (MULTI_LINE_COMMENT.containsMatchIn(line)) {
        comment = !comment
      } else if (!comment && !SINGLE_LINE_COMMENT.containsMatchIn(line)) {
        events += classify(line, index + 1)
      }
    }
    return run(events)
  }

  /** What Yadda makes of a Markdown feature (`MarkdownFeatureParser`). */
  fun analyzeMarkdown(text: String): Result {
    val scan = MarkdownScan()
    val events = mutableListOf<Event>()
    for ((index, line) in text.split("\r\n", "\n").withIndex()) scan.line(line, index + 1, events)
    return run(events)
  }

  /** Feeds [events] to the constructs until one of them throws, as Yadda does line by line. */
  private fun run(events: List<Event>): Result {
    val context = Context()
    val specification = Specification(context)
    var current: Node = specification
    var issue: Issue? = null
    try {
      for (event in events) current = current.on(event)
      specification.finish()
    } catch (failure: Failure) {
      issue = Issue(failure.line, failure.message, failure.reported)
    }
    return Result(issue, context.rules, context.steps, events.mapNotNull(::markerOf), context.examples)
  }

  private fun markerOf(event: Event): Marker? {
    val kind = when (event.kind) {
      Kind.ANNOTATION -> MarkerKind.ANNOTATION
      Kind.FEATURE -> MarkerKind.FEATURE
      Kind.RULE -> MarkerKind.RULE
      Kind.SCENARIO -> MarkerKind.SCENARIO
      Kind.BACKGROUND -> MarkerKind.BACKGROUND
      Kind.EXAMPLES -> MarkerKind.EXAMPLES
      else -> return null
    }
    return Marker(kind, event.line, event.title.trim(), event.key, event.value, event.column)
  }

  /** A line's event, in the order Yadda tries the line classes. */
  private fun classify(line: String, number: Int): Event {
    SIMPLE_ANNOTATION.find(line)?.let { return annotation(number, line, it.groupValues[1].trim(), null) }
    NVP_ANNOTATION.find(line)?.let { return annotation(number, line, it.groupValues[1].trim(), it.groupValues[2].trim()) }
    FEATURE.find(line)?.let { return Event(Kind.FEATURE, number, title = it.groupValues[1], column = keywordColumn(line)) }
    RULE.find(line)?.let { return Event(Kind.RULE, number, title = it.groupValues[1], column = keywordColumn(line)) }
    SCENARIO.find(line)?.let { return Event(Kind.SCENARIO, number, title = it.groupValues[1], column = keywordColumn(line)) }
    BACKGROUND.find(line)?.let { return Event(Kind.BACKGROUND, number, title = it.groupValues[1], column = keywordColumn(line)) }
    if (EXAMPLES.containsMatchIn(line)) return Event(Kind.EXAMPLES, number, column = keywordColumn(line))
    if (BLANK.containsMatchIn(line)) return Event(Kind.BLANK, number, data = line)
    DASH.find(line)?.let { return Event(Kind.DASH, number, data = it.groupValues[1]) }
    return Event(Kind.TEXT, number, data = line)
  }

  private fun annotation(number: Int, line: String, key: String, value: String?) =
    Event(Kind.ANNOTATION, number, key = key, data = value ?: "true", value = value, column = keywordColumn(line))

  private fun keywordColumn(line: String): Int = line.length - line.trimStart().length

  private val MD_HEADING = Regex("""^\s*#+\s*(.*)$""")
  private val MD_FENCE = Regex("""^\s*(`{3,}|~{3,})""")
  private val MD_COMMENT_OPEN = Regex("""^\s*<!--""")
  private val MD_COMMENT_CLOSE = Regex("""-->""")
  private val MD_BLOCKQUOTE = Regex("""^\s*>""")
  private val MD_LIST_ITEM = Regex("""^\s*[-*+]\s+(.*)$""")
  private val MD_TABLE_ROW = Regex("""^\s*\|""")
  private val MD_SEPARATOR_CELL = Regex("""^\s*:?-+:?\s*$""")
  private val MD_ENTITY = Regex("""&(#x?[0-9a-f]+|[a-z]+);""", RegexOption.IGNORE_CASE)
  private val MD_NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")

  /** `decode_entities`: `&lt;` and its kind back to the character, and anything else left as written. */
  internal fun decodeEntities(text: String): String =
    MD_ENTITY.replace(text) { match ->
      val code = match.groupValues[1]
      if (code[0] == '#') {
        val hex = code[1].lowercaseChar() == 'x'
        // parseInt reads as many digits as it can, so "#12ab" is 12.
        val digits = if (hex) code.drop(2) else code.drop(1).takeWhile { it.isDigit() }
        val value = digits.toIntOrNull(if (hex) 16 else 10)
        if (value == null || !Character.isValidCodePoint(value)) match.value else String(Character.toChars(value))
      } else {
        MD_NAMED_ENTITIES[code.lowercase()] ?: match.value
      }
    }

  private fun stripOuterPipes(line: String): String = line.trim().removePrefix("|").removeSuffix("|").trim()

  private fun isTableSeparator(line: String): Boolean = stripOuterPipes(line).split('|').all { MD_SEPARATOR_CELL.matches(it) }

  /**
   * `MarkdownFeatureParser.parse_line`: a heading is a keyword line, a list item or plain line is a step,
   * a fenced block or a table is a dashed block around lines, and a blockquote or `<!-- -->` is a comment.
   * It remembers whether it is inside a fence, a comment or a table, which the lines alone don't say.
   */
  private class MarkdownScan {
    private var inFence = false
    private var fenceMarker = ' '
    private var inComment = false
    private var inTable = false

    fun line(line: String, number: Int, out: MutableList<Event>) {
      if (inComment) {
        inComment = !MD_COMMENT_CLOSE.containsMatchIn(line)
        return
      }
      if (inFence && closesFence(line)) {
        inFence = false
        out += Event(Kind.DASH, number, data = line)
        return
      }
      if (inFence) {
        out += Event(Kind.TEXT, number, data = line)
        return
      }
      if (MD_COMMENT_OPEN.containsMatchIn(line)) {
        inComment = !MD_COMMENT_CLOSE.containsMatchIn(line)
        return
      }
      SIMPLE_ANNOTATION.find(line)?.let {
        out += annotation(number, line, it.groupValues[1].trim(), null)
        return
      }
      NVP_ANNOTATION.find(line)?.let {
        out += annotation(number, line, it.groupValues[1].trim(), it.groupValues[2].trim())
        return
      }
      if (inTable && isTableSeparator(line)) return
      if (MD_TABLE_ROW.containsMatchIn(line)) {
        if (!inTable) {
          inTable = true
          out += Event(Kind.DASH, number, data = "---")
        }
        out += Event(Kind.TEXT, number, data = decodeEntities(stripOuterPipes(line)))
        return
      }
      if (inTable) {
        inTable = false
        out += Event(Kind.DASH, number, data = "---")
      }
      MD_FENCE.find(line)?.let {
        inFence = true
        fenceMarker = it.groupValues[1][0]
        out += Event(Kind.DASH, number, data = line)
        return
      }
      if (MD_BLOCKQUOTE.containsMatchIn(line)) return
      if (BLANK.containsMatchIn(line)) {
        out += Event(Kind.BLANK, number, data = line)
        return
      }
      MD_HEADING.find(line)?.let {
        out += heading(it.groupValues[1], it.groups[1]!!.range.first, number)
        return
      }
      MD_LIST_ITEM.find(line)?.let {
        out += Event(Kind.TEXT, number, data = decodeEntities(it.groupValues[1]), column = it.groups[1]!!.range.first)
        return
      }
      out += Event(Kind.TEXT, number, data = decodeEntities(line))
    }

    private fun heading(text: String, column: Int, number: Int): Event {
      FEATURE.find(text)?.let { return Event(Kind.FEATURE, number, title = it.groupValues[1], column = column) }
      RULE.find(text)?.let { return Event(Kind.RULE, number, title = it.groupValues[1], column = column) }
      SCENARIO.find(text)?.let { return Event(Kind.SCENARIO, number, title = it.groupValues[1], column = column) }
      BACKGROUND.find(text)?.let { return Event(Kind.BACKGROUND, number, title = it.groupValues[1], column = column) }
      if (EXAMPLES.containsMatchIn(text)) return Event(Kind.EXAMPLES, number, column = column)
      return Event(Kind.TEXT, number, data = decodeEntities(text), column = column)
    }

    private fun closesFence(line: String): Boolean = Regex("""^\s*${Regex.escape(fenceMarker.toString())}{3,}\s*$""").containsMatchIn(line)
  }

  /** Replaces `[heading]` in [line] for each field, in the order the fields were read. */
  internal fun substitute(fields: Map<String, List<String>>, line: String): String {
    var result = line
    for ((heading, values) in fields) {
      // The heading is part of the pattern as written, as it is in Yadda.
      val pattern = try {
        Regex("""\[\s*$heading\s*\]""")
      } catch (_: IllegalArgumentException) {
        continue
      }
      val value = values.joinToString("\n").trimEnd()
      result = pattern.replace(result) { value }
    }
    return result
  }

  private fun indentation(text: String): Int = text.takeWhile { it.isWhitespace() }.length

  private fun isNotBlank(text: String): Boolean = text.isNotBlank()

  private enum class Kind(val label: String) {
    ANNOTATION("Annotation"),
    FEATURE("Feature"),
    RULE("Rule"),
    SCENARIO("Scenario"),
    BACKGROUND("Background"),
    EXAMPLES("Examples"),
    BLANK("Blank"),
    DASH("Dash"),
    TEXT("Text"),
  }

  /** [column] is where [data] (or the keyword, for a heading) starts in its line; [title] is what follows a heading's keyword. */
  private class Event(
    val kind: Kind,
    val line: Int,
    val data: String = "",
    val key: String = "",
    val column: Int = 0,
    val title: String = "",
    val value: String? = null,
  )

  private class Failure(val line: Int, override val message: String, val reported: Int? = line) : Exception(message)

  private class Context {
    val rules = mutableListOf<Int>()
    val steps = linkedMapOf<Int, Step>()
    val examples = mutableListOf<ExamplesInfo>()
  }

  private typealias Handler = (Event) -> Node?

  /** A construct, and the events it accepts right now. */
  private abstract class Node(val context: Context) {
    val handlers = LinkedHashMap<Kind, Handler>()

    fun on(event: Event): Node {
      val handler = handlers[event.kind] ?: throw Failure(event.line, "${event.kind.label} is unexpected at this time")
      return handler(event) ?: this
    }

    fun unregister(vararg kinds: Kind) = kinds.forEach { handlers.remove(it) }
  }

  private fun stash(event: Event) {
    if (event.key.any { it.isWhitespace() }) throw Failure(event.line, "Invalid annotation: ${event.key}")
  }

  /**
   * Steps in a Background or a Scenario, which are read the same way, fenced blocks and all. A line of
   * one of the [interruptions] kinds ends the construct, even in the middle of a block.
   */
  private abstract class StepsNode(
    context: Context,
    val line: Int,
    private val noun: String,
    private val interruptions: List<Kind>,
    title: String = "",
  ) : Node(context) {
    private val steps = mutableListOf<Step>()
    private var blanks = mutableListOf<String>()
    private var indentation = 0
    protected val scope = Scope(title)

    init {
      handlers[Kind.TEXT] = ::captureStep
      handlers[Kind.BLANK] = { null }
      interruptions.forEach { handlers[it] = ::interrupt }
    }

    /** What happens when something other than a step comes along. */
    protected abstract fun interrupt(event: Event): Node?

    protected fun validate(reported: Int?) {
      if (steps.isEmpty()) throw Failure(line, "$noun requires one or more steps", reported)
    }

    private fun captureStep(event: Event): Node? {
      handlers[Kind.DASH] = ::enableMultiLineStep
      val step = Step(event.line, event.data.trim(), scope, event.column + event.data.length - event.data.trimStart().length)
      steps.add(step)
      scope.steps.add(step)
      context.steps[event.line] = step
      return null
    }

    private fun enableMultiLineStep(event: Event): Node? {
      unregister(Kind.DASH, Kind.ANNOTATION, Kind.SCENARIO, Kind.RULE, Kind.EXAMPLES)
      handlers[Kind.TEXT] = ::startMultiLineStep
      handlers[Kind.BLANK] = ::stashBlanks
      indentation = indentation(event.data)
      return null
    }

    private fun startMultiLineStep(event: Event): Node? {
      handlers[Kind.DASH] = ::disableMultiLineStep
      handlers[Kind.TEXT] = ::continueMultiLineStep
      handlers[Kind.BLANK] = ::stashBlanks
      interruptions.forEach { handlers[it] = ::interrupt }
      append(event, event.data)
      return null
    }

    private fun continueMultiLineStep(event: Event): Node? {
      unstashBlanks(event)
      append(event, event.data)
      return null
    }

    private fun stashBlanks(event: Event): Node? {
      blanks.add(event.data)
      return null
    }

    private fun unstashBlanks(event: Event) {
      if (blanks.isEmpty()) return
      append(event, blanks.joinToString("\n"))
      blanks = mutableListOf()
    }

    private fun disableMultiLineStep(event: Event): Node? {
      unregister(Kind.DASH)
      handlers[Kind.TEXT] = ::captureStep
      handlers[Kind.BLANK] = { null }
      unstashBlanks(event)
      return null
    }

    private fun append(event: Event, text: String) {
      if (isNotBlank(text) && indentation(text) < indentation) throw Failure(event.line, "Indentation error")
      val last = steps.last()
      last.text = last.text + "\n" + text.drop(indentation).trimEnd()
    }
  }

  private class Background(context: Context, line: Int, private val parent: Node) :
    StepsNode(context, line, "Background", listOf(Kind.ANNOTATION, Kind.SCENARIO, Kind.RULE)) {
    override fun interrupt(event: Event): Node? {
      validate(event.line)
      return parent.on(event)
    }
  }

  private class Scenario(context: Context, line: Int, title: String, private val parent: Node) :
    StepsNode(context, line, "Scenario", listOf(Kind.ANNOTATION, Kind.SCENARIO, Kind.RULE, Kind.EXAMPLES), title) {
    private var examples: Examples? = null

    override fun interrupt(event: Event): Node? {
      validate(event.line)
      if (event.kind == Kind.EXAMPLES) return Examples(context, event.line, this, scope).also { examples = it }
      return parent.on(event)
    }

    /** Hands an event Examples has no use for back to the parent, as the scenario would. */
    fun resume(event: Event): Node = on(event)

    fun finish() {
      validate(null)
      examples?.validate(null)
    }
  }

  private class Examples(context: Context, private val line: Int, private val scenario: Scenario, private val scope: Scope) :
    Node(context) {
    private class Heading(val text: String, val left: Int, val indentation: Int)

    private var headings = listOf<Heading>()
    private val rows = mutableListOf<LinkedHashMap<String, MutableList<String>>>()

    init {
      handlers[Kind.BLANK] = { null }
      handlers[Kind.DASH] = ::startExampleTable
      handlers[Kind.TEXT] = ::captureHeadings
    }

    private fun startExampleTable(event: Event): Node? {
      unregister(Kind.BLANK, Kind.DASH)
      return null
    }

    private fun captureHeadings(event: Event): Node? {
      handlers[Kind.ANNOTATION] = ::stashAnnotation
      handlers[Kind.TEXT] = ::captureSingleLineFields
      handlers[Kind.DASH] = ::enableMultiLineExamples
      var position = 1
      headings = split(event.data, null, event).map { column ->
        Heading(column.trim(), position, indentation(column)).also { position += column.length + 1 }
      }
      context.examples.add(ExamplesInfo(line, event.line, headings.map { it.text }, scope))
      return null
    }

    private fun stashAnnotation(event: Event): Node? {
      unregister(Kind.BLANK, Kind.DASH)
      stash(event)
      return null
    }

    private fun captureSingleLineFields(event: Event): Node? {
      handlers[Kind.DASH] = ::endExampleTable
      handlers[Kind.BLANK] = ::endExampleTable
      rows.add(parseFields(event.data, linkedMapOf(), event))
      addMetaFields(event.line)
      return null
    }

    private fun enableMultiLineExamples(event: Event): Node? {
      handlers[Kind.TEXT] = ::startCapturingMultiLineFields
      handlers[Kind.DASH] = ::stopCapturingMultiLineFields
      return null
    }

    private fun startCapturingMultiLineFields(event: Event): Node? {
      handlers[Kind.TEXT] = ::continueCapturingMultiLineFields
      handlers[Kind.DASH] = ::stopCapturingMultiLineFields
      handlers[Kind.BLANK] = ::endExampleTable
      rows.add(parseFields(event.data, linkedMapOf(), event))
      addMetaFields(event.line)
      return null
    }

    private fun continueCapturingMultiLineFields(event: Event): Node? {
      parseFields(event.data, rows.last(), event)
      return null
    }

    private fun stopCapturingMultiLineFields(event: Event): Node? {
      handlers[Kind.TEXT] = ::startCapturingMultiLineFields
      return null
    }

    private fun endExampleTable(event: Event): Node? {
      unregister(Kind.TEXT, Kind.DASH)
      handlers[Kind.BLANK] = { null }
      handlers[Kind.ANNOTATION] = ::leave
      handlers[Kind.SCENARIO] = ::leave
      handlers[Kind.RULE] = ::leave
      return null
    }

    private fun addMetaFields(line: Int) {
      val fields = rows.last()
      if (scope.first == null) scope.first = fields
      for (heading in headings) {
        fields["${heading.text}.index"] = mutableListOf(rows.size.toString())
        fields["${heading.text}.start.line"] = mutableListOf(line.toString())
        fields["${heading.text}.start.column"] = mutableListOf((heading.left + heading.indentation).toString())
      }
    }

    private fun parseFields(row: String, fields: LinkedHashMap<String, MutableList<String>>, event: Event): LinkedHashMap<String, MutableList<String>> {
      for ((index, field) in split(row, headings.size, event).withIndex()) {
        val heading = headings[index]
        val text = field.drop(heading.indentation).trimEnd()
        if (isNotBlank(field) && indentation(field) < heading.indentation) throw Failure(event.line, "Indentation error")
        fields.getOrPut(heading.text) { mutableListOf() }.add(text)
      }
      return fields
    }

    private fun split(row: String, expected: Int?, event: Event): List<String> {
      val fields = row.split(if ('┆' in row) "┆" else "|")
      if (expected != null && expected != fields.size) {
        throw Failure(event.line, "Incorrect number of fields in example table. Expected $expected but found ${fields.size}")
      }
      return fields
    }

    private fun leave(event: Event): Node? {
      validate(event.line)
      return scenario.resume(event)
    }

    fun validate(reported: Int?) {
      if (headings.isEmpty()) throw Failure(line, "Examples table requires one or more headings", reported)
      if (rows.isEmpty()) throw Failure(line, "Examples table requires one or more rows", reported)
    }
  }

  private class Rule(context: Context, private val line: Int, private val feature: Feature) : Node(context) {
    private val scenarios = mutableListOf<Scenario>()

    init {
      handlers[Kind.TEXT] = { null }
      handlers[Kind.BLANK] = { null }
      handlers[Kind.ANNOTATION] = ::stashAnnotation
      handlers[Kind.SCENARIO] = ::startScenario
      handlers[Kind.BACKGROUND] = ::startBackground
      handlers[Kind.RULE] = ::delegate
    }

    private fun startBackground(event: Event): Node = Background(context, event.line, this)

    private fun stashAnnotation(event: Event): Node? {
      unregister(Kind.BACKGROUND, Kind.TEXT)
      stash(event)
      return null
    }

    private fun startScenario(event: Event): Node = Scenario(context, event.line, event.title.trim(), this).also { scenarios.add(it) }

    private fun delegate(event: Event): Node? {
      validate(event.line)
      return feature.on(event)
    }

    private fun validate(reported: Int?) {
      if (scenarios.isEmpty()) throw Failure(line, "Rule requires one or more scenarios", reported)
    }

    fun finish() {
      validate(null)
      scenarios.forEach { it.finish() }
    }
  }

  private class Feature(context: Context, private val line: Int) : Node(context) {
    private val scenarios = mutableListOf<Scenario>()
    private val rules = mutableListOf<Rule>()

    init {
      handlers[Kind.TEXT] = { null }
      handlers[Kind.BLANK] = { null }
      handlers[Kind.ANNOTATION] = ::stashAnnotation
      handlers[Kind.SCENARIO] = ::startScenario
      handlers[Kind.BACKGROUND] = ::startBackground
      handlers[Kind.RULE] = ::startRule
    }

    private fun startBackground(event: Event): Node = Background(context, event.line, this)

    private fun stashAnnotation(event: Event): Node? {
      unregister(Kind.BACKGROUND, Kind.TEXT)
      stash(event)
      return null
    }

    private fun startScenario(event: Event): Node = Scenario(context, event.line, event.title.trim(), this).also { scenarios.add(it) }

    private fun startRule(event: Event): Node {
      context.rules.add(event.line)
      return Rule(context, event.line, this).also { rules.add(it) }
    }

    fun finish() {
      if (scenarios.isEmpty() && rules.isEmpty()) throw Failure(line, "Feature requires one or more scenarios", null)
      scenarios.forEach { it.finish() }
      rules.forEach { it.finish() }
    }
  }

  /** Where every file starts: anything before a `Feature:` is ignored, and a scenario with no feature gets one. */
  private class Specification(context: Context) : Node(context) {
    private var feature: Feature? = null

    init {
      handlers[Kind.TEXT] = { null }
      handlers[Kind.BLANK] = { null }
      handlers[Kind.ANNOTATION] = ::stashAnnotation
      handlers[Kind.FEATURE] = ::startFeature
      handlers[Kind.RULE] = ::startImplicitFeature
      handlers[Kind.SCENARIO] = ::startImplicitFeature
      handlers[Kind.BACKGROUND] = ::startImplicitFeature
    }

    private fun stashAnnotation(event: Event): Node? {
      unregister(Kind.BACKGROUND)
      stash(event)
      return null
    }

    private fun startFeature(event: Event): Node = Feature(context, event.line).also { feature = it }

    private fun startImplicitFeature(event: Event): Node = startFeature(event).on(event)

    fun finish() {
      (feature ?: throw Failure(1, "A feature must contain one or more scenarios", null)).finish()
    }
  }
}
