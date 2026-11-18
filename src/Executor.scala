package how

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Base64
import scala.util.{Try, Using}

object Executor:

  /** Was gestartet wird, als Wert: das Programm mit Argumenten und gegebenenfalls ein Skript, das als letztes Argument
    * (Pfad einer temporären Datei) angehängt wird.
    */
  final case class Plan(program: List[String], script: Option[String])

  /** Variablen, die das Startskript nur für `how` selbst setzt; der ausgeführte Befehl soll sie nicht erben. */
  val LauncherVariables: Set[String] = Set("MSYS_NO_PATHCONV", "HOW_BASH")

  private val Yes = Set("y", "yes", "j", "ja")

  /** Nur ausdrückliche Zustimmung zählt; alles andere, auch eine leere Eingabe, ist nein. */
  def isYes(input: String): Boolean = Yes.contains(input.trim.toLowerCase)

  /** Plant den Aufruf. Der Befehl wird nie als Argument übergeben, weil Java unter Windows Anführungszeichen in
    * Argumenten nicht zuverlässig umsetzt: bash liest ihn aus einer Datei, PowerShell bekommt ihn Base64-kodiert. Nur
    * für bash wird nach dem Programm gesucht.
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

  /** PowerShell erwartet für `-EncodedCommand` UTF-16LE in Base64. */
  def encodeForPowerShell(command: String): String =
    Base64.getEncoder.encodeToString(command.getBytes(StandardCharsets.UTF_16LE))

  /** Wählt die bash. Unter Windows findet `CreateProcess` in System32 den WSL-Starter `bash.exe` vor dem PATH; deshalb
    * wird dort nie bloß „bash“ gestartet, sondern ein gefundener Pfad, in dieser Reihenfolge: die bash, die `how`
    * gestartet hat (`HOW_BASH`, vom Startskript), die aus `SHELL` und die erste im PATH. WSL-Starter in System32,
    * Sysnative und WindowsApps werden übergangen.
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

  /** Prüft, ob eine Datei existiert; ein ungültiger Pfad heißt nein, nicht Exception. */
  def fileExists(path: String): Boolean = Try(Files.isRegularFile(Path.of(path))).getOrElse(false)

  /** Führt den Plan mit geerbter Ein- und Ausgabe aus und liefert den Exit-Code. */
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

  /** Löscht die Skriptdatei, auch wenn der Prozess nicht startet. */
  private final class Deleting(path: Path) extends AutoCloseable:
    def close(): Unit = Files.deleteIfExists(path): Unit
