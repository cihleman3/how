package how

/** Alles, was bei einem Aufruf von `how` schiefgehen kann. */
enum HowError:
  case Usage(problem: String)
  case MissingApiKey
  case InvalidApiKey
  case NoCredits
  case RateLimited
  case Http(status: Int, detail: String)
  case Upstream(detail: String)
  case Unreachable(reason: String)
  case InvalidResponse(reason: String)
  case Execution(reason: String)

  def message: String = this match
    case Usage(problem) => s"$problem\nTry 'how --help'."
    case MissingApiKey  => "OPENROUTER_API_KEY is not set. Create a key at https://openrouter.ai/keys and export it."
    case InvalidApiKey  => "OpenRouter rejected the API key (HTTP 401). Check OPENROUTER_API_KEY."
    case NoCredits      => "OpenRouter says the account has no credits left (HTTP 402)."
    case RateLimited    => "OpenRouter rate limit reached (HTTP 429). Wait a moment and try again."
    case Http(status, detail)    => s"OpenRouter answered with HTTP $status: $detail"
    case Upstream(detail)        => s"The model provider reported an error: $detail"
    case Unreachable(reason)     => s"Could not reach OpenRouter: $reason"
    case InvalidResponse(reason) => s"The model's answer could not be understood: $reason"
    case Execution(reason)       => s"Could not run the command: $reason"

  /** 2 für Bedienfehler, 1 für alles andere. */
  def exitCode: Int = this match
    case Usage(_) => 2
    case _        => 1

object HowError:

  /** Einheitliche Beschreibung einer gefangenen Exception für die Fehlermeldung. Bibliotheken wie sttp verpacken die
    * eigentliche Ursache („connection refused“) in eine allgemeine Exception; deshalb zählt die innerste Meldung.
    */
  def describe(e: Throwable): String =
    val chain = LazyList.iterate(Option(e))(_.flatMap(t => Option(t.getCause))).take(10).takeWhile(_.isDefined).flatten
    chain.reverse
      .flatMap(t => Option(t.getMessage).map(_.trim).filter(_.nonEmpty))
      .headOption
      .getOrElse(e.getClass.getSimpleName)
