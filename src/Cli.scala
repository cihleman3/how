package how

import scala.annotation.tailrec

/** Ergebnis der Kommandozeile. Ohne Frage (`None`) liest `App` sie interaktiv ein. */
final case class CliArgs(
    shell: Option[ShellChoice] = None,
    model: Option[String] = None,
    question: Option[String] = None,
    help: Boolean = false
)

object Cli:

  val usage: String =
    """Usage: how [options] <question in plain words>
      |       how [options]            (asks for the question; use this if it contains ' * ? % & or similar)
      |
      |Prints a command ready to copy & paste and a short explanation, then offers to run it.
      |
      |Options:
      |  --shell <name>   bash, powershell (PowerShell 7 if installed, else 5.1), pwsh, powershell5
      |                   (default: bash if SHELL is set, else PowerShell on Windows)
      |  --model <id>     OpenRouter model (default: $HOW_MODEL or anthropic/claude-haiku-5.5)
      |  --help           show this help
      |  --               end of options; everything after it is the question
      |
      |Needs the environment variable OPENROUTER_API_KEY.""".stripMargin

  /** Optionen werden nur am Anfang erkannt. Ab dem ersten Wort der Frage gehört alles zur Frage, auch `--force`. */
  def parse(args: List[String]): Either[HowError, CliArgs] =
    @tailrec
    def loop(rest: List[String], acc: CliArgs): Either[HowError, CliArgs] =
      rest match
        case Nil                                            => Right(acc)
        case "--" :: words                                  => Right(acc.copy(question = question(words)))
        case ("--help" | "-h") :: tail                      => loop(tail, acc.copy(help = true))
        case "--shell" :: value :: tail if !isOption(value) =>
          Shell.parse(value) match
            case Some(choice) => loop(tail, acc.copy(shell = Some(choice)))
            case None => Left(HowError.Usage(s"Unknown shell '$value'. Use bash, powershell, pwsh or powershell5."))
        case "--model" :: value :: tail if !isOption(value) && value.trim.nonEmpty =>
          loop(tail, acc.copy(model = Some(value.trim)))
        case (option @ ("--shell" | "--model")) :: _ => Left(HowError.Usage(s"$option needs a value."))
        case word :: _ if isOption(word)             =>
          Left(HowError.Usage(s"Unknown option '$word'. Put -- before a question that starts with '-'."))
        case words => Right(acc.copy(question = question(words)))

    loop(args, CliArgs())

  private def isOption(word: String): Boolean = word.startsWith("-") && word.length > 1

  /** Verbindet die Wörter mit Leerzeichen; eine leere Frage gilt als keine Frage. */
  def question(words: List[String]): Option[String] =
    Some(words.mkString(" ").trim).filter(_.nonEmpty)
