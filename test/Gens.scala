package how

import org.scalacheck.Gen

object Gens:

  /** Beliebiger druckbarer ASCII-Text: auch leer, Leerzeichen, Anführungszeichen, `*`, `?`, `&`. */
  val text: Gen[String] = Gen.asciiPrintableStr

  /** Garantiert nicht leer und nicht nur Leerraum. */
  val nonBlank: Gen[String] = Gen.nonEmptyStringOf(Gen.asciiPrintableChar).suchThat(_.trim.nonEmpty)

  /** Ohne Leerraum am Rand, wie nach `trim`. */
  val trimmed: Gen[String] = nonBlank.map(_.trim)

  /** Leer oder nur Leerraum. */
  val blank: Gen[String] = Gen.listOf(Gen.oneOf(' ', '\t')).map(_.mkString)

  /** Ein- oder mehrzeilig, jede Zeile ohne Leerraum am Rand. */
  val lines: Gen[String] = Gen.nonEmptyListOf(trimmed).map(_.mkString("\n"))

  /** Steuerzeichen außer Zeilenumbruch und Tabulator: C0, DEL und C1. */
  val controlChar: Gen[Char] = Gen.oneOf(
    Gen.choose(0, 31).suchThat(c => c != '\n'.toInt && c != '\t'.toInt).map(_.toChar),
    Gen.const(127.toChar),
    Gen.choose(128, 159).map(_.toChar)
  )

  /** Unsichtbare Formatzeichen, mit denen sich Text anders anzeigen lässt, als er ist (Bidi, Nullbreite, BOM). */
  val formatChar: Gen[Char] = Gen.oneOf(0x200b, 0x200e, 0x200f, 0x202a, 0x202e, 0x2066, 0x2069, 0xfeff).map(_.toChar)

  /** Fügt das Zeichen an beliebiger Stelle ein. */
  def withInserted(base: String, c: Char): Gen[String] =
    Gen.choose(0, base.length).map(i => base.take(i) + c + base.drop(i))

  /** Wörter einer Frage, wie die Shell sie übergibt. */
  val words: Gen[List[String]] = Gen.listOf(text)

  /** Ein Wort, das nicht als Option gilt: beginnt nicht mit `-` oder ist genau `-`. */
  val questionStart: Gen[String] = Gen.oneOf(Gen.const("-"), text.suchThat(w => !w.startsWith("-")))

  /** Fast gleiche Varianten: andere Groß-/Kleinschreibung und Leerraum am Rand. */
  def nearlyEqual(value: String): Gen[String] =
    for
      cased <- Gen.oneOf(value, value.toUpperCase, value.capitalize, value.toLowerCase)
      before <- Gen.oneOf("", " ", "  ", "\t")
      after <- Gen.oneOf("", " ", "\t ")
    yield before + cased + after

  /** Gleiche Buchstaben, Groß-/Kleinschreibung beliebig gemischt. */
  def anyCase(value: String): Gen[String] =
    Gen
      .listOfN(value.length, Gen.oneOf(true, false))
      .map: flags =>
        value.zip(flags).map((c, upper) => if upper then c.toUpper else c.toLower).mkString

  val shell: Gen[Shell] = Gen.oneOf(Shell.values.toSeq)

  val shellChoice: Gen[ShellChoice] = Gen.oneOf(ShellChoice.values.toSeq)

  val windowsName: Gen[String] =
    Gen.oneOf("Windows 11", "Windows 10", "Windows Server 2025").flatMap(anyCase)

  val otherOsName: Gen[String] = Gen
    .oneOf(Gen.oneOf("Linux", "Mac OS X", "FreeBSD", ""), text)
    .suchThat(name => !name.toLowerCase.startsWith("windows"))

  val anyOsName: Gen[String] = Gen.oneOf(windowsName, otherOsName)

  /** Umgebung ohne `SHELL`; die Schlüssel stammen aus einem kleinen Pool, damit Kollisionen vorkommen. */
  val envWithoutShell: Gen[Map[String, String]] =
    val keys = Gen.oneOf("PATH", "TERM", "HOME", "shell", "Shell", "SHELL ", "COMSPEC")
    Gen.mapOf(Gen.zip(keys, text))

  /** Verzeichnisse mit dem WSL-Starter `bash.exe`, in allen Schreibweisen, die im PATH vorkommen. */
  val wslDir: Gen[String] = Gen.oneOf(
    "C:\\Windows\\System32",
    "c:/windows/system32",
    "C:\\Windows\\System32\\",
    "\"C:\\Windows\\System32\"",
    "C:\\Users\\u\\AppData\\Local\\Microsoft\\WindowsApps",
    "C:/Users/u/AppData/Local/Microsoft/WindowsApps/",
    "C:\\Windows\\Sysnative"
  )

  /** Verzeichnisse einer Git-Installation. */
  val gitDir: Gen[String] = Gen.oneOf(
    "C:\\Program Files\\Git\\bin",
    "C:/Program Files/Git/usr/bin/",
    "\"C:\\Program Files\\Git\\bin\"",
    "D:\\tools\\git\\bin\\"
  )

  /** Ein PATH-Eintrag: WSL, Git, Sonderfälle (leer, Anführungszeichen, Laufwerkswurzel) oder beliebiger Text. */
  val pathEntry: Gen[String] = Gen.oneOf(
    wslDir,
    gitDir,
    Gen.oneOf("", " ", "\"\"", "\"C:\\Program Files\\Foo\"", "C:\\a\"b", "C:\\", "\\\\server\\share"),
    text.suchThat(!_.contains(';'))
  )

  val windowsPath: Gen[String] = Gen.listOf(pathEntry).map(_.mkString(";"))

  val rawAnswer: Gen[RawAnswer] =
    for
      kind <- Gen.oneOf(Gen.oneOf("command", "clarification"), text)
      command <- text
      explanation <- text
      warning <- text
      question <- text
    yield RawAnswer(kind, command, explanation, warning, question)

  val answer: Gen[Answer] =
    Gen.oneOf(
      Gen.zip(lines, lines, Gen.option(lines)).map(Answer.Command.apply),
      lines.map(Answer.Clarification.apply)
    )
