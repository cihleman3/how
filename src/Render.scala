package how

object Render:

  private val Escape = 27.toChar
  private val Reset = s"$Escape[0m"
  private val Yellow = s"$Escape[33m"

  /** The command stands alone on its line (or lines), without prefix and never colored: that way it stays cleanly
    * copyable even when color detection is wrong (Git Bash sets `TERM` even for redirected output).
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

  /** Colors only in a terminal and only if `NO_COLOR` is not set (https://no-color.org). */
  def useColor(env: Map[String, String], interactive: Boolean): Boolean =
    interactive && !env.get("NO_COLOR").exists(_.nonEmpty) && !env.get("TERM").contains("dumb")
