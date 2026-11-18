package how

/** All effects `how` needs. `Main` provides the real ones, the tests their own. */
trait Io:
  def interactive: Boolean
  def readLine(prompt: String): Option[String]
  def out(text: String): Unit
  def err(text: String): Unit
  def ask(config: Config, systemPrompt: String, question: String): Either[HowError, RawAnswer]
  def exists(path: String): Boolean
  def execute(plan: Executor.Plan): Either[HowError, Int]

/** The flow of one call. Returns the exit code: 0 success, 1 error, 2 clarification or usage error. */
object App:

  def run(args: List[String], env: Map[String, String], osName: String, io: Io): Int =
    Cli.parse(args) match
      case Left(error)            => fail(io, error)
      case Right(cli) if cli.help =>
        io.out(Cli.usage)
        0
      case Right(cli) =>
        val shell = Shell.resolve(cli.shell, env, osName, Executables.find("pwsh", env, osName, io.exists).isDefined)
        val outcome =
          for
            config <- Config.fromEnv(env, cli.model)
            question <- cli.question
              .orElse(io.readLine("Your question: ").map(Input.clean).flatMap(line => Cli.question(List(line))))
              .toRight(HowError.Usage("No question given."))
            raw <- io.ask(config, Prompt.system(shell, osName), question)
            answer <- Answer.from(raw)
          yield answer
        outcome match
          case Left(error)   => fail(io, error)
          case Right(answer) =>
            io.out(Render.answer(answer, Render.useColor(env, io.interactive)))
            answer match
              case Answer.Clarification(_)       => 2
              case Answer.Command(command, _, _) => offerToRun(io, shell, command, env, osName)

  /** The warning is right above in the answer and is not repeated. */
  private def offerToRun(io: Io, shell: Shell, command: String, env: Map[String, String], osName: String): Int =
    val confirmed = io.readLine(s"Run it in ${shell.label}? [y/N] ").map(Input.clean).exists(Executor.isYes)
    if !confirmed then 0
    else
      Executor.plan(shell, command, env, osName, io.exists).flatMap(io.execute) match
        case Left(error)     => fail(io, error)
        case Right(exitCode) => exitCode

  private def fail(io: Io, error: HowError): Int =
    io.err(s"how: ${error.message}")
    error.exitCode
