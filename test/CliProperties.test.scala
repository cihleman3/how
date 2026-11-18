package how

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class CliProperties extends ScalaCheckSuite:

  private def isUsageError(result: Either[HowError, CliArgs]): Boolean =
    result.left.exists(_.isInstanceOf[HowError.Usage])

  property("after -- every word belongs to the question, even options"):
    forAll(Gens.words): words =>
      assertEquals(Cli.parse("--" :: words), Right(CliArgs(question = Cli.question(words))))

  property("bare words are joined verbatim with single spaces"):
    forAllNoShrink(Gens.questionStart, Gens.words): (first, rest) =>
      val words = first :: rest
      assertEquals(Cli.parse(words).map(_.question), Right(Cli.question(words)))
      assertEquals(Cli.question(words).getOrElse(""), words.mkString(" ").trim)

  property("options before the question are taken out, words after the first question word are not"):
    val shellNames = Gen.oneOf("bash", "powershell", "pwsh", "powershell5")
    forAllNoShrink(shellNames, Gens.trimmed.suchThat(!_.startsWith("-")), Gens.questionStart, Gens.words):
      (shellName, model, first, rest) =>
        val words = first :: rest ++ List("--shell", "bash", "--help")
        val args = List("--model", model, "--shell", shellName) ++ words
        assertEquals(
          Cli.parse(args),
          Right(CliArgs(shell = Shell.parse(shellName), model = Some(model), question = Cli.question(words)))
        )

  property("no words or only blanks means no question"):
    forAll(Gen.listOf(Gens.blank)): blanks =>
      assertEquals(Cli.parse("--" :: blanks), Right(CliArgs()))

  property("shell names are recognised in any case and with blanks around them"):
    val names = Map(
      "bash" -> ShellChoice.Bash,
      "powershell" -> ShellChoice.AnyPowerShell,
      "pwsh" -> ShellChoice.PowerShell7,
      "powershell7" -> ShellChoice.PowerShell7,
      "powershell5" -> ShellChoice.WindowsPowerShell
    )
    forAllNoShrink(Gen.oneOf(names.toSeq).flatMap((name, choice) => Gens.nearlyEqual(name).map(_ -> choice))):
      (value, choice) => assertEquals(Cli.parse(List("--shell", value)).map(_.shell), Right(Some(choice)))

  property("an unknown shell is a usage error"):
    forAllNoShrink(Gens.text.suchThat(v => Shell.parse(v).isEmpty)): value =>
      assert(isUsageError(Cli.parse(List("--shell", value, "x"))))

  property("an option is never taken as the value of --shell or --model"):
    val options = Gen.oneOf(
      Gen.oneOf("--help", "-h", "--", "--shell", "--model", "-x"),
      Gens.text.map("-" + _).suchThat(_.length > 1)
    )
    forAllNoShrink(Gen.oneOf("--shell", "--model"), options, Gens.words): (option, value, rest) =>
      assert(isUsageError(Cli.parse(option :: value :: rest)), s"$option $value")

  property("near-equal options are not taken for real ones"):
    val nearOptions = Gen.oneOf("--Shell", "--SHELL", "--shell=bash", "--Model", "--HELP", "-shell", "---")
    forAllNoShrink(nearOptions, Gens.words): (option, rest) =>
      assert(isUsageError(Cli.parse(option :: rest)), option)

  property("an option with blanks in front is a word of the question"):
    forAllNoShrink(Gen.oneOf(" --shell", "  --help", "\t--model"), Gens.words): (word, rest) =>
      assertEquals(Cli.parse(word :: rest).map(_.question), Right(Cli.question(word :: rest)))

  test("--help is recognised"):
    assertEquals(Cli.parse(List("--help")), Right(CliArgs(help = true)))
    assertEquals(Cli.parse(List("-h")), Right(CliArgs(help = true)))

  test("an option without a value is a usage error"):
    assert(isUsageError(Cli.parse(List("--shell"))))
    assert(isUsageError(Cli.parse(List("--model"))))
    assert(isUsageError(Cli.parse(List("--model", " ", "x"))))

  // Shell

  property("bash and PowerShell 7 are taken as asked, on every OS"):
    forAllNoShrink(Gens.envWithoutShell, Gens.anyOsName, Gen.oneOf(true, false)): (env, os, pwsh) =>
      assertEquals(Shell.resolve(Some(ShellChoice.Bash), env, os, pwsh), Shell.Bash)
      assertEquals(Shell.resolve(Some(ShellChoice.PowerShell7), env, os, pwsh), Shell.PowerShell7)

  property("powershell means PowerShell 7 if it is there, on Windows otherwise Windows PowerShell 5.1"):
    forAllNoShrink(Gens.envWithoutShell, Gens.windowsName, Gens.otherOsName): (env, windows, other) =>
      assertEquals(Shell.resolve(Some(ShellChoice.AnyPowerShell), env, windows, true), Shell.PowerShell7)
      assertEquals(Shell.resolve(Some(ShellChoice.AnyPowerShell), env, windows, false), Shell.WindowsPowerShell)
      assertEquals(Shell.resolve(Some(ShellChoice.AnyPowerShell), env, other, false), Shell.PowerShell7)

  property("powershell5 is Windows PowerShell on Windows and PowerShell 7 elsewhere"):
    forAllNoShrink(Gens.envWithoutShell, Gens.windowsName, Gens.otherOsName, Gen.oneOf(true, false)):
      (env, windows, other, pwsh) =>
        assertEquals(Shell.resolve(Some(ShellChoice.WindowsPowerShell), env, windows, pwsh), Shell.WindowsPowerShell)
        assertEquals(Shell.resolve(Some(ShellChoice.WindowsPowerShell), env, other, pwsh), Shell.PowerShell7)

  property("without --shell: a non-blank SHELL means bash, whatever the OS"):
    forAllNoShrink(Gens.envWithoutShell, Gens.nonBlank, Gens.anyOsName, Gen.oneOf(true, false)):
      (env, shellPath, os, pwsh) =>
        assertEquals(Shell.resolve(None, env + ("SHELL" -> shellPath), os, pwsh), Shell.Bash)

  property("without --shell and SHELL: Windows means PowerShell (7 if there), everything else bash"):
    forAllNoShrink(Gens.envWithoutShell, Gens.blank, Gens.windowsName, Gens.otherOsName, Gen.oneOf(true, false)):
      (env, blankShell, windows, other, pwsh) =>
        val expected = if pwsh then Shell.PowerShell7 else Shell.WindowsPowerShell
        assertEquals(Shell.resolve(None, env, windows, pwsh), expected)
        assertEquals(Shell.resolve(None, env + ("SHELL" -> blankShell), windows, pwsh), expected)
        assertEquals(Shell.resolve(None, env, other, pwsh), Shell.Bash)

  test("PowerShell 7 is only looked for when it matters"):
    def unexpected: Boolean = fail("pwsh lookup not expected")
    assertEquals(Shell.resolve(Some(ShellChoice.Bash), Map.empty, "Windows 11", unexpected), Shell.Bash)
    assertEquals(Shell.resolve(None, Map("SHELL" -> "/bin/bash"), "Windows 11", unexpected), Shell.Bash)
