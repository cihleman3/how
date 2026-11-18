package how

object Input:

  private val Bom = 0xfeff.toChar

  /** Cleans a line that was read: in a pipe, PowerShell 5.1 puts a BOM in front of the text. */
  def clean(line: String): String = line.stripPrefix(Bom.toString)
