package how

/** Everything that can go wrong in a call of `how`. */
enum HowError:
  case Usage(problem: String)

  /** `terminal` is the shell the user types in; the message shows how to set the key there. */
  case MissingApiKey(terminal: Shell)
  case InvalidApiKey
  case NoCredits
  case RateLimited
  case Http(status: Int, detail: String)
  case Upstream(detail: String)
  case Unreachable(reason: String)
  case InvalidResponse(reason: String)
  case Execution(reason: String)

  def message: String = this match
    case Usage(problem)          => s"$problem\nTry 'how --help'."
    case MissingApiKey(terminal) => HowError.missingKey(terminal)
    case InvalidApiKey           => "OpenRouter rejected the API key (HTTP 401). Check OPENROUTER_API_KEY."
    case NoCredits               => "OpenRouter says the account has no credits left (HTTP 402)."
    case RateLimited             => "OpenRouter rate limit reached (HTTP 429). Wait a moment and try again."
    case Http(status, detail)    => s"OpenRouter answered with HTTP $status: $detail"
    case Upstream(detail)        => s"The model provider reported an error: $detail"
    case Unreachable(reason)     => s"Could not reach OpenRouter: $reason"
    case InvalidResponse(reason) => s"The model's answer could not be understood: $reason"
    case Execution(reason)       => s"Could not run the command: $reason"

  /** 2 for usage errors, 1 for everything else. */
  def exitCode: Int = this match
    case Usage(_) => 2
    case _        => 1

object HowError:

  private def missingKey(terminal: Shell): String =
    val setIt = terminal match
      case Shell.Bash =>
        """  export OPENROUTER_API_KEY=<key>
          |and add that line to your shell's startup file (e.g. ~/.bashrc or ~/.zshrc) to keep it.""".stripMargin
      case Shell.WindowsPowerShell | Shell.PowerShell7 =>
        """  [Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', '<key>', 'User')
          |and open a new terminal. In cmd: setx OPENROUTER_API_KEY <key>""".stripMargin
    s"OPENROUTER_API_KEY is not set. Create a key at https://openrouter.ai/keys, then run:\n$setIt"

  /** Uniform description of a caught exception for the error message. Libraries like sttp wrap the actual cause
    * ("connection refused") in a generic exception, so the innermost message counts.
    */
  def describe(e: Throwable): String =
    val chain = LazyList.iterate(Option(e))(_.flatMap(t => Option(t.getCause))).take(10).takeWhile(_.isDefined).flatten
    chain.reverse
      .flatMap(t => Option(t.getMessage).map(_.trim).filter(_.nonEmpty))
      .headOption
      .getOrElse(e.getClass.getSimpleName)
