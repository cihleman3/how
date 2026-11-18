package how

import java.util.Base64
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

/** Properties of Config, Answer, Render, Input, Prompt and the pure parts of Executor. */
class CoreProperties extends ScalaCheckSuite:

  private val Bom = 0xfeff.toChar

  /** ANSI color code, to remove it from the output. */
  private val Ansi = 27.toChar.toString + "\\[[0-9;]*m"

  // Config

  property("the API key is taken from the environment without blanks around it"):
    forAllNoShrink(Gens.trimmed, Gens.blank, Gens.blank, Gens.shell): (key, before, after, terminal) =>
      val env = Map("OPENROUTER_API_KEY" -> s"$before$key$after")
      assertEquals(Config.fromEnv(env, None, terminal).map(_.apiKey), Right(key))

  property("a missing or blank API key is an error that knows the user's shell"):
    forAllNoShrink(Gen.option(Gens.blank), Gens.shell): (maybeBlank, terminal) =>
      val env = maybeBlank.map(k => Map("OPENROUTER_API_KEY" -> k)).getOrElse(Map.empty)
      assertEquals(Config.fromEnv(env, None, terminal), Left(HowError.MissingApiKey(terminal)))

  property("the missing-key message says how to set the key in the user's shell"):
    forAllNoShrink(Gens.shell): terminal =>
      val message = HowError.MissingApiKey(terminal).message
      assert(message.contains("https://openrouter.ai/keys"), message)
      terminal match
        case Shell.Bash =>
          assert(message.contains("export OPENROUTER_API_KEY=<key>"), message)
          assert(!message.contains("SetEnvironmentVariable"), message)
        case Shell.WindowsPowerShell | Shell.PowerShell7 =>
          assert(message.contains("[Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', '<key>', 'User')"))
          assert(message.contains("setx OPENROUTER_API_KEY <key>"), message)
          assert(!message.contains("export"), message)

  property("--model wins over HOW_MODEL, which wins over the default"):
    forAllNoShrink(Gen.option(Gens.trimmed), Gen.option(Gen.oneOf(Gens.trimmed, Gens.blank))): (option, fromEnv) =>
      val env = Map("OPENROUTER_API_KEY" -> "k") ++ fromEnv.map("HOW_MODEL" -> _)
      val expected = option.orElse(fromEnv.filter(_.trim.nonEmpty)).getOrElse(Config.DefaultModel)
      assertEquals(Config.fromEnv(env, option, Shell.Bash).map(_.model), Right(expected))

  // Answer

  property("RawAnswer survives the way through JSON"):
    forAll(Gens.rawAnswer): raw =>
      assertEquals(upickle.default.read[RawAnswer](upickle.default.write(raw)), raw)

  property("a command answer keeps command and explanation, a blank warning becomes None"):
    forAllNoShrink(Gens.lines, Gens.lines, Gen.oneOf(Gens.text, Gens.blank)): (command, explanation, warning) =>
      assertEquals(
        Answer.from(RawAnswer("command", command, explanation, warning, "ignored")),
        Right(Answer.Command(command, explanation, Some(warning.trim).filter(_.nonEmpty)))
      )

  property("Windows line endings become \\n"):
    forAllNoShrink(Gens.lines, Gens.lines): (command, explanation) =>
      val crlf = (text: String) => text.replace("\n", "\r\n")
      assertEquals(
        Answer.from(RawAnswer("command", crlf(command), crlf(explanation), "", "")),
        Right(Answer.Command(command, explanation, None))
      )

  private def tainted(chars: Gen[Char]): Gen[String] =
    for
      text <- Gens.lines
      c <- chars
      result <- Gens.withInserted(text, c)
    yield result

  property("a command with control characters is rejected, so what is shown is what runs"):
    forAllNoShrink(tainted(Gens.controlChar)): command =>
      assert(Answer.from(RawAnswer("command", command, "explained", "", "")).isLeft, command.map(_.toInt))

  property("a command with invisible formatting characters is rejected"):
    forAllNoShrink(tainted(Gens.formatChar)): command =>
      assert(Answer.from(RawAnswer("command", command, "explained", "", "")).isLeft, command.map(_.toInt))

  property("control characters are rejected in every shown text, not only in the command"):
    forAllNoShrink(tainted(Gens.controlChar)): text =>
      assert(Answer.from(RawAnswer("command", "ls", text, "", "")).isLeft)
      assert(Answer.from(RawAnswer("command", "ls", "lists", text, "")).isLeft)
      assert(Answer.from(RawAnswer("clarification", "", "", "", text)).isLeft)

  property("a command answer without command or explanation is invalid"):
    forAllNoShrink(Gens.blank, Gens.nonBlank, Gens.text): (blank, filled, warning) =>
      assert(Answer.from(RawAnswer("command", blank, filled, warning, "")).isLeft)
      assert(Answer.from(RawAnswer("command", filled, blank, warning, "")).isLeft)

  property("a clarification needs a question"):
    forAllNoShrink(Gens.nonBlank, Gens.blank, Gens.text): (question, blank, other) =>
      assertEquals(
        Answer.from(RawAnswer("clarification", other, other, other, question)),
        Right(Answer.Clarification(question.trim))
      )
      assert(Answer.from(RawAnswer("clarification", other, other, other, blank)).isLeft)

  property("any other kind is invalid, also near-equal spellings"):
    val kinds = Gen.oneOf(
      Gen
        .oneOf("command", "clarification")
        .flatMap(Gens.nearlyEqual)
        .suchThat(k => k != "command" && k != "clarification"),
      Gens.text.suchThat(k => k != "command" && k != "clarification")
    )
    forAllNoShrink(kinds, Gens.nonBlank): (kind, filled) =>
      assert(Answer.from(RawAnswer(kind, filled, filled, filled, filled)).isLeft, kind)

  // Render

  property("the command stands verbatim at the top, without escape codes even with color"):
    forAllNoShrink(Gens.lines, Gens.lines, Gen.option(Gens.lines), Gen.oneOf(true, false)):
      (command, explanation, warning, color) =>
        val output = Render.answer(Answer.Command(command, explanation, warning), color)
        val commandLines = command.linesIterator.size
        assertEquals(output.linesIterator.take(commandLines).mkString("\n"), command)
        assertEquals(output.linesIterator.drop(commandLines).next(), "")
        val plain = output.replaceAll(Ansi, "")
        assert(plain.contains(explanation))
        warning.foreach(w => assert(plain.contains(w)))

  property("without color there are no escape codes at all"):
    forAllNoShrink(Gens.answer): answer =>
      assert(!Render.answer(answer, color = false).contains(27.toChar))

  property("color only adds escape codes"):
    forAllNoShrink(Gens.answer): answer =>
      val stripped = Render.answer(answer, color = true).replaceAll(Ansi, "")
      assertEquals(stripped, Render.answer(answer, color = false))

  property("no color when NO_COLOR is set, TERM is dumb or the output is no terminal"):
    forAllNoShrink(Gens.envWithoutShell, Gens.nonBlank): (env, value) =>
      assert(!Render.useColor(env + ("NO_COLOR" -> value), interactive = true))
      assert(!Render.useColor(env + ("TERM" -> "dumb"), interactive = true))
      assert(!Render.useColor(env, interactive = false))

  // HowError

  test("an error is described by its innermost message"):
    val wrapped = RuntimeException("Exception when sending request", java.net.ConnectException("connection refused"))
    assertEquals(HowError.describe(wrapped), "connection refused")
    assertEquals(HowError.describe(RuntimeException("outer", RuntimeException())), "outer")
    assertEquals(HowError.describe(IllegalStateException()), "IllegalStateException")

  // Input

  property("a BOM at the start of an input line, as PowerShell 5.1 sends it through a pipe, is removed"):
    forAll(Gens.text): input =>
      assertEquals(Input.clean(Bom.toString + input), Input.clean(input))
      assertEquals(Input.clean(input), input)

  // Prompt

  test("the prompt never suggests && or || for Windows PowerShell 5.1, but allows them elsewhere"):
    val windowsPowerShell = Prompt.system(Shell.WindowsPowerShell, "Windows 11")
    assert(windowsPowerShell.contains("Windows PowerShell 5.1 on Windows 11"))
    assert(windowsPowerShell.contains("never use && or ||"))
    assert(!windowsPowerShell.contains("; or &&"))
    Seq(Shell.Bash, Shell.PowerShell7).foreach: shell =>
      assert(Prompt.system(shell, "Linux").contains("; or &&"), shell)

  // Executor

  property("yes in any case and with blanks around it means yes"):
    forAllNoShrink(Gen.oneOf("y", "yes", "j", "ja").flatMap(Gens.anyCase), Gens.blank, Gens.blank):
      (yes, before, after) => assert(Executor.isYes(s"$before$yes$after"))

  property("everything else means no"):
    forAllNoShrink(Gens.text.suchThat(t => !Set("y", "yes", "j", "ja").contains(t.trim.toLowerCase))): input =>
      assert(!Executor.isYes(input), input)

  private def isWslLauncher(path: String): Boolean =
    val lower = path.toLowerCase.replace('/', '\\')
    Seq("\\system32\\", "\\windowsapps\\", "\\sysnative\\").exists(lower.contains)

  property("on Windows the WSL launcher is never chosen and nothing throws, whatever is in PATH, SHELL or HOW_BASH"):
    forAllNoShrink(Gens.windowsPath, Gen.option(Gens.pathEntry), Gen.option(Gens.pathEntry), Gens.windowsName):
      (path, shellDir, launcherDir, os) =>
        val env = Map("Path" -> path) ++ shellDir.map(d => "SHELL" -> s"$d\\bash.exe") ++
          launcherDir.map(d => "HOW_BASH" -> s"$d\\bash.exe")
        Executor.chooseBash(env, os, _ => true) match
          case Right(bash) => assert(!isWslLauncher(bash), bash)
          case Left(error) => assert(error.isInstanceOf[HowError.Execution])

  property("on Windows the bash that started how comes first, then SHELL, then PATH"):
    forAllNoShrink(Gens.gitDir, Gens.gitDir, Gens.windowsPath, Gens.windowsName): (launcherDir, shellDir, path, os) =>
      val launcherBash = s"$launcherDir\\bash.exe"
      val shellBash = s"$shellDir\\bash.exe"
      val env = Map("PATH" -> path, "SHELL" -> shellBash, "HOW_BASH" -> launcherBash)
      val first = Executor.chooseBash(env, os, _ => true)
      assertEquals(first, Right(Executables.cleanFile(os)(launcherBash)))
      assertEquals(Executor.chooseBash(env - "HOW_BASH", os, _ => true), Right(Executables.cleanFile(os)(shellBash)))

  property("on Windows only an existing bash is chosen, and none means a clear error"):
    forAllNoShrink(Gens.windowsPath, Gens.windowsName): (path, os) =>
      assertEquals(
        Executor.chooseBash(Map("PATH" -> path), os, _ => false),
        Left(HowError.Execution("no bash found: install Git for Windows or use --shell powershell"))
      )

  property("elsewhere bash is simply bash"):
    forAllNoShrink(Gens.envWithoutShell, Gens.otherOsName): (env, os) =>
      assertEquals(Executor.chooseBash(env, os, _ => false), Right("bash"))

  property("PowerShell gets the command exactly, encoded, and needs neither bash nor a script"):
    forAllNoShrink(Gen.oneOf(Shell.WindowsPowerShell, Shell.PowerShell7), Gens.lines, Gens.windowsPath, Gens.anyOsName):
      (shell, command, path, os) =>
        val plan = Executor.plan(shell, command, Map("PATH" -> path), os, _ => fail("no file system access expected"))
        val expectedProgram = if shell == Shell.PowerShell7 then "pwsh" else "powershell"
        plan match
          case Right(Executor.Plan(program, script)) =>
            assertEquals(program.take(3), List(expectedProgram, "-NoProfile", "-EncodedCommand"))
            assertEquals(String(Base64.getDecoder.decode(program(3)), "UTF-16LE"), command)
            assertEquals(script, None)
          case Left(error) => fail(s"unexpected $error")

  property("bash reads the command from a script; the command itself is never an argument"):
    forAllNoShrink(Gens.lines, Gens.anyOsName): (command, os) =>
      val env = Map("PATH" -> "C:\\Program Files\\Git\\bin")
      Executor.plan(Shell.Bash, command, env, os, _ => true) match
        case Right(Executor.Plan(program, script)) =>
          assertEquals(script, Some(command + "\n"))
          assert(!program.contains(command))
        case Left(error) => fail(s"unexpected $error")
