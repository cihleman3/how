package how

/** The shell that `how` writes commands for and runs them in. */
enum Shell(val label: String):
  case Bash extends Shell("bash")
  case WindowsPowerShell extends Shell("Windows PowerShell 5.1")
  case PowerShell7 extends Shell("PowerShell 7")

/** What `--shell` asked for. `AnyPowerShell` means: PowerShell 7 if available. */
enum ShellChoice:
  case Bash, AnyPowerShell, PowerShell7, WindowsPowerShell

object Shell:

  /** Reads the value of `--shell`; case and surrounding whitespace do not matter. */
  def parse(value: String): Option[ShellChoice] =
    value.trim.toLowerCase match
      case "bash"                 => Some(ShellChoice.Bash)
      case "powershell"           => Some(ShellChoice.AnyPowerShell)
      case "pwsh" | "powershell7" => Some(ShellChoice.PowerShell7)
      case "powershell5"          => Some(ShellChoice.WindowsPowerShell)
      case _                      => None

  /** Determines the shell. Without `--shell`: bash if `SHELL` is set (Git Bash, macOS, Linux), otherwise PowerShell on
    * Windows. PowerShell means PowerShell 7 if available; Windows PowerShell 5.1 exists only on Windows.
    *
    * @param pwshAvailable
    *   evaluated only when it matters for the result (it searches the file system)
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
