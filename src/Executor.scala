package how

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Base64
import scala.util.{Try, Using}

object Executor:

  /** What gets started, as a value: the program with its arguments and, if needed, a script that is appended as the
    * last argument (path of a temporary file).
    */
  final case class Plan(program: List[String], script: Option[String])

  /** Variables the launcher sets only for `how` itself; the command that `how` runs must not inherit them. */
  val LauncherVariables: Set[String] = Set("MSYS_NO_PATHCONV", "HOW_BASH")

  private val Yes = Set("y", "yes", "j", "ja")

  /** Only explicit consent counts; anything else, including empty input, means no. */
  def isYes(input: String): Boolean = Yes.contains(input.trim.toLowerCase)

  /** Plans the call. The command is never passed as an argument, because Java does not reliably quote arguments on
    * Windows: bash reads it from a file, PowerShell gets it Base64-encoded. Only bash is looked up as a program.
    */
  def plan(
      shell: Shell,
      command: String,
      env: Map[String, String],
      osName: String,
      exists: String => Boolean
  ): Either[HowError, Plan] =
    shell match
      case Shell.Bash => chooseBash(env, osName, exists).map(bash => Plan(List(bash), Some(command + "\n")))
      case Shell.WindowsPowerShell => Right(powerShell("powershell", command))
      case Shell.PowerShell7       => Right(powerShell("pwsh", command))

  private def powerShell(executable: String, command: String): Plan =
    Plan(List(executable, "-NoProfile", "-EncodedCommand", encodeForPowerShell(command)), None)

  /** PowerShell expects UTF-16LE in Base64 for `-EncodedCommand`. */
  def encodeForPowerShell(command: String): String =
    Base64.getEncoder.encodeToString(command.getBytes(StandardCharsets.UTF_16LE))

  /** Chooses the bash. On Windows, `CreateProcess` finds the WSL launcher `bash.exe` in System32 before the PATH, so
    * plain "bash" is never started there, only a path that was found, in this order: the bash that started `how`
    * (`HOW_BASH`, set by the launcher), the one from `SHELL`, and the first one on the PATH. WSL launchers in System32,
    * Sysnative and WindowsApps are skipped.
    */
  def chooseBash(env: Map[String, String], osName: String, exists: String => Boolean): Either[HowError, String] =
    if !Shell.isWindows(osName) then Right("bash")
    else
      val fromLauncher = env.get("HOW_BASH").toList
      val fromShell = env.get("SHELL").filter(_.trim.toLowerCase.endsWith("bash.exe")).toList
      val fromPath = Executables.candidates("bash", env, osName)
      (fromLauncher ++ fromShell ++ fromPath)
        .map(Executables.cleanFile(osName))
        .filterNot(isWslLauncher)
        .find(exists)
        .toRight(HowError.Execution("no bash found: install Git for Windows or use --shell powershell"))

  private def isWslLauncher(path: String): Boolean =
    val lower = path.toLowerCase
    List("\\system32\\", "\\sysnative\\", "\\windowsapps\\").exists(lower.contains)

  /** Checks whether a file exists; an invalid path means no, not an exception. */
  def fileExists(path: String): Boolean = Try(Files.isRegularFile(Path.of(path))).getOrElse(false)

  /** Runs the plan with inherited input and output and returns the exit code. */
  def run(plan: Plan): Either[HowError, Int] =
    Try:
      plan.script match
        case None         => start(plan.program)
        case Some(script) =>
          val file = Files.createTempFile("how-", ".sh")
          Using.resource(Deleting(file)): _ =>
            Files.writeString(file, script, StandardCharsets.UTF_8)
            start(plan.program :+ file.toString)
    .toEither.left.map(e => HowError.Execution(HowError.describe(e)))

  private def start(commandLine: List[String]): Int =
    val builder = ProcessBuilder(commandLine*).inheritIO()
    LauncherVariables.foreach(builder.environment().remove)
    builder.start().waitFor()

  /** Deletes the script file, even if the process does not start. */
  private final class Deleting(path: Path) extends AutoCloseable:
    def close(): Unit = Files.deleteIfExists(path): Unit
