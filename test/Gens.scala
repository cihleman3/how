package how

import org.scalacheck.Gen

object Gens:

  /** Any printable ASCII text: also empty, spaces, quotes, `*`, `?`, `&`. */
  val text: Gen[String] = Gen.asciiPrintableStr

  /** Guaranteed not empty and not only whitespace. */
  val nonBlank: Gen[String] = Gen.nonEmptyStringOf(Gen.asciiPrintableChar).suchThat(_.trim.nonEmpty)

  /** Without surrounding whitespace, as after `trim`. */
  val trimmed: Gen[String] = nonBlank.map(_.trim)

  /** Empty or only whitespace. */
  val blank: Gen[String] = Gen.listOf(Gen.oneOf(' ', '\t')).map(_.mkString)

  /** One or more lines, each without surrounding whitespace. */
  val lines: Gen[String] = Gen.nonEmptyListOf(trimmed).map(_.mkString("\n"))

  /** Control characters except newline and tab: C0, DEL and C1. */
  val controlChar: Gen[Char] = Gen.oneOf(
    Gen.choose(0, 31).suchThat(c => c != '\n'.toInt && c != '\t'.toInt).map(_.toChar),
    Gen.const(127.toChar),
    Gen.choose(128, 159).map(_.toChar)
  )

  /** Invisible formatting characters that make text display differently from what it is (bidi, zero width, BOM). */
  val formatChar: Gen[Char] = Gen.oneOf(0x200b, 0x200e, 0x200f, 0x202a, 0x202e, 0x2066, 0x2069, 0xfeff).map(_.toChar)

  /** Inserts the character at any position. */
  def withInserted(base: String, c: Char): Gen[String] =
    Gen.choose(0, base.length).map(i => base.take(i) + c + base.drop(i))

  /** Words of a question, as the shell passes them. */
  val words: Gen[List[String]] = Gen.listOf(text)

  /** A word that does not count as an option: does not start with `-`, or is exactly `-`. */
  val questionStart: Gen[String] = Gen.oneOf(Gen.const("-"), text.suchThat(w => !w.startsWith("-")))

  /** Nearly equal variants: different case and surrounding whitespace. */
  def nearlyEqual(value: String): Gen[String] =
    for
      cased <- Gen.oneOf(value, value.toUpperCase, value.capitalize, value.toLowerCase)
      before <- Gen.oneOf("", " ", "  ", "\t")
      after <- Gen.oneOf("", " ", "\t ")
    yield before + cased + after

  /** Same letters, with upper and lower case mixed at random. */
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

  /** Environment without `SHELL`; the keys come from a small pool, so collisions happen. */
  val envWithoutShell: Gen[Map[String, String]] =
    val keys = Gen.oneOf("PATH", "TERM", "HOME", "shell", "Shell", "SHELL ", "COMSPEC")
    Gen.mapOf(Gen.zip(keys, text))

  /** Directories with the WSL launcher `bash.exe`, in all spellings that occur in PATH. */
  val wslDir: Gen[String] = Gen.oneOf(
    "C:\\Windows\\System32",
    "c:/windows/system32",
    "C:\\Windows\\System32\\",
    "\"C:\\Windows\\System32\"",
    "C:\\Users\\u\\AppData\\Local\\Microsoft\\WindowsApps",
    "C:/Users/u/AppData/Local/Microsoft/WindowsApps/",
    "C:\\Windows\\Sysnative"
  )

  /** Directories of a Git installation. */
  val gitDir: Gen[String] = Gen.oneOf(
    "C:\\Program Files\\Git\\bin",
    "C:/Program Files/Git/usr/bin/",
    "\"C:\\Program Files\\Git\\bin\"",
    "D:\\tools\\git\\bin\\"
  )

  /** A PATH entry: WSL, Git, special cases (empty, quotes, drive root) or any text. */
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
