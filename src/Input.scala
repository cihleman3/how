package how

object Input:

  private val Bom = 0xfeff.toChar

  /** Bereinigt eine eingelesene Zeile: PowerShell 5.1 setzt in einer Pipe ein BOM vor den Text. */
  def clean(line: String): String = line.stripPrefix(Bom.toString)
