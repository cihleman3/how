package how

import upickle.default.ReadWriter

/** Antwort des Modells, wie sie über die Leitung kommt. Alle Felder sind Pflicht (strict JSON Schema), „keins“ ist "".
  */
final case class RawAnswer(kind: String, command: String, explanation: String, warning: String, question: String)
    derives ReadWriter

object RawAnswer:

  /** JSON Schema für `response_format`; muss zu den Feldern von [[RawAnswer]] passen. */
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

/** Geprüfte Antwort: ohne Steuerzeichen, Zeilenenden als `\n`. */
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

  /** Geprüft wird vor `trim`: Auch ein Steuerzeichen am Rand macht die Antwort verdächtig. */
  private def unifyLineEnds(text: String): String = text.replace("\r\n", "\n")

  /** Steuerzeichen außer Zeilenumbruch und Tabulator. Ein `\r` oder eine ESC-Sequenz kann im Terminal Text
    * überschreiben, sodass ein anderer Befehl angezeigt wird als der, der nach „y“ läuft.
    */
  private def isControl(c: Char): Boolean =
    Character.getType(c) == Character.CONTROL && c != '\n' && c != '\t'

  /** Unsichtbare Formatzeichen (Bidi-Steuerung, Nullbreite, BOM), mit denen ein Befehl anders aussieht, als er ist. */
  private def isFormatting(c: Char): Boolean = Character.getType(c) == Character.FORMAT
