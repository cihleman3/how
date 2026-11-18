package how

object Render:

  private val Escape = 27.toChar
  private val Reset = s"$Escape[0m"
  private val Yellow = s"$Escape[33m"

  /** Der Befehl steht allein auf seiner Zeile (bzw. seinen Zeilen), ohne Präfix und immer ohne Farbe: So bleibt er auch
    * dann sauber kopierbar, wenn die Farberkennung irrt (Git Bash setzt `TERM` auch bei umgeleiteter Ausgabe).
    */
  def answer(answer: Answer, color: Boolean): String =
    def paint(text: String): String =
      if color then text.linesIterator.map(line => s"$Yellow$line$Reset").mkString("\n") else text

    answer match
      case Answer.Command(command, explanation, warning) =>
        val parts = List(command, "", explanation) ++ warning.toList.flatMap(w => List("", paint(s"Warning: $w")))
        parts.mkString("\n")
      case Answer.Clarification(question) =>
        s"${paint("Your question is not clear enough yet:")}\n$question\n\nPlease ask again with more detail."

  /** Farben nur im Terminal und nur, wenn `NO_COLOR` nicht gesetzt ist (https://no-color.org). */
  def useColor(env: Map[String, String], interactive: Boolean): Boolean =
    interactive && !env.get("NO_COLOR").exists(_.nonEmpty) && !env.get("TERM").contains("dumb")
