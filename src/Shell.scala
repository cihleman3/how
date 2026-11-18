package how

/** Die Shell, für die `how` Befehle erzeugt und in der es sie ausführt. */
enum Shell(val label: String):
  case Bash extends Shell("bash")
  case WindowsPowerShell extends Shell("Windows PowerShell 5.1")
  case PowerShell7 extends Shell("PowerShell 7")

/** Was mit `--shell` verlangt wurde. `AnyPowerShell` heißt: PowerShell 7, wenn vorhanden. */
enum ShellChoice:
  case Bash, AnyPowerShell, PowerShell7, WindowsPowerShell

object Shell:

  /** Liest den Wert von `--shell`; Groß-/Kleinschreibung und Leerraum am Rand spielen keine Rolle. */
  def parse(value: String): Option[ShellChoice] =
    value.trim.toLowerCase match
      case "bash"                 => Some(ShellChoice.Bash)
      case "powershell"           => Some(ShellChoice.AnyPowerShell)
      case "pwsh" | "powershell7" => Some(ShellChoice.PowerShell7)
      case "powershell5"          => Some(ShellChoice.WindowsPowerShell)
      case _                      => None

  /** Bestimmt die Shell. Ohne `--shell`: bash, wenn `SHELL` gesetzt ist (Git Bash, macOS, Linux), sonst unter Windows
    * PowerShell. PowerShell heißt PowerShell 7, wenn vorhanden; Windows PowerShell 5.1 gibt es nur unter Windows.
    *
    * @param pwshAvailable
    *   wird nur ausgewertet, wenn es auf die Antwort ankommt (Suche im Dateisystem)
    */
  def resolve(choice: Option[ShellChoice], env: Map[String, String], osName: String, pwshAvailable: => Boolean): Shell =
    def anyPowerShell: Shell =
      if isWindows(osName) && !pwshAvailable then WindowsPowerShell else PowerShell7

    choice match
      case Some(ShellChoice.Bash)              => Bash
      case Some(ShellChoice.PowerShell7)       => PowerShell7
      case Some(ShellChoice.WindowsPowerShell) => if isWindows(osName) then WindowsPowerShell else PowerShell7
      case Some(ShellChoice.AnyPowerShell)     => anyPowerShell
      case None                                =>
        if env.get("SHELL").exists(_.trim.nonEmpty) then Bash
        else if isWindows(osName) then anyPowerShell
        else Bash

  def isWindows(osName: String): Boolean = osName.toLowerCase.startsWith("windows")
