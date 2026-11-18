package how

import upickle.default.ReadWriter

/** The model's answer as it comes over the wire. All fields are required (strict JSON Schema); "none" is "". */
final case class RawAnswer(kind: String, command: String, explanation: String, warning: String, question: String)
    derives ReadWriter

object RawAnswer:

  /** JSON Schema for `response_format`; must match the fields of [[RawAnswer]]. */
  val schema: ujson.Value =
    val text = ujson.Obj("type" -> "string")
    ujson.Obj(
      "type" -> "object",
      "properties" -> ujson.Obj(
        "kind" -> ujson.Obj("type" -> "string", "enum" -> ujson.Arr("command", "clarification")),
        "command" -> text,
        "explanation" -> text,
        "warning" -> text,
        "question" -> text
      ),
      "required" -> ujson.Arr("kind", "command", "explanation", "warning", "question"),
      "additionalProperties" -> false
    )

/** Checked answer: no control characters, line endings as `\n`. */
enum Answer:
  case Command(command: String, explanation: String, warning: Option[String])
  case Clarification(question: String)

object Answer:

  def from(raw: RawAnswer): Either[HowError, Answer] =
    raw.kind match
      case "command" =>
        val command = unifyLineEnds(raw.command)
        val explanation = unifyLineEnds(raw.explanation)
        val warning = unifyLineEnds(raw.warning)
        if List(command, explanation, warning).exists(_.exists(isControl)) then Left(controlCharacters)
        else if command.exists(isFormatting) then
          Left(HowError.InvalidResponse("the command contains invisible formatting characters; not shown for safety"))
        else if command.trim.isEmpty then Left(HowError.InvalidResponse("the answer contains no command"))
        else if explanation.trim.isEmpty then Left(HowError.InvalidResponse("the answer contains no explanation"))
        else Right(Command(command.trim, explanation.trim, Some(warning.trim).filter(_.nonEmpty)))
      case "clarification" =>
        val question = unifyLineEnds(raw.question)
        if question.exists(isControl) then Left(controlCharacters)
        else if question.trim.isEmpty then
          Left(HowError.InvalidResponse("the model asked back, but without a question"))
        else Right(Clarification(question.trim))
      case other => Left(HowError.InvalidResponse(s"unknown kind of answer '$other'"))

  private val controlCharacters =
    HowError.InvalidResponse("the answer contains control characters; not shown for safety")

  /** Checked before `trim`: a control character at the edge also makes the answer suspicious. */
  private def unifyLineEnds(text: String): String = text.replace("\r\n", "\n")

  /** Control characters except newline and tab. A `\r` or an ESC sequence can overwrite text in the terminal, so that a
    * different command is shown than the one that runs after "y".
    */
  private def isControl(c: Char): Boolean =
    Character.getType(c) == Character.CONTROL && c != '\n' && c != '\t'

  /** Invisible formatting characters (bidi controls, zero width, BOM) that make a command look different from what it
    * is.
    */
  private def isFormatting(c: Char): Boolean = Character.getType(c) == Character.FORMAT
