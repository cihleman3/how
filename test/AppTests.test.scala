package how

import java.util.concurrent.atomic.AtomicReference
import munit.FunSuite

/** The wiring in [[App.run]]: exit codes, order, and that something runs only after consent. */
class AppTests extends FunSuite:

  enum Event:
    case Out(text: String)
    case Err(text: String)
    case Prompt(text: String)
    case Asked(model: String, question: String)
    case Executed(plan: Executor.Plan)

  /** Test Io: returns given inputs and answers and records everything. */
  final class FakeIo(
      inputs: List[String] = Nil,
      reply: Either[HowError, RawAnswer] = Right(command("ls")),
      execution: Either[HowError, Int] = Right(0),
      existing: String => Boolean = _ => true
  ) extends Io:
    private val remaining = AtomicReference(inputs)
    private val recorded = AtomicReference(Vector.empty[Event])

    def events: Vector[Event] = recorded.get
    private def record(event: Event): Unit = recorded.updateAndGet(_ :+ event): Unit

    def interactive: Boolean = false
    def readLine(prompt: String): Option[String] =
      record(Event.Prompt(prompt))
      remaining.getAndUpdate(_.drop(1)).headOption
    def out(text: String): Unit = record(Event.Out(text))
    def err(text: String): Unit = record(Event.Err(text))
    def ask(config: Config, systemPrompt: String, question: String): Either[HowError, RawAnswer] =
      record(Event.Asked(config.model, question))
      reply
    def exists(path: String): Boolean = existing(path)
    def execute(plan: Executor.Plan): Either[HowError, Int] =
      record(Event.Executed(plan))
      execution

    def asked: Vector[Event.Asked] = events.collect { case a: Event.Asked => a }
    def executed: Vector[Executor.Plan] = events.collect { case Event.Executed(plan) => plan }
    def prompts: Vector[String] = events.collect { case Event.Prompt(text) => text }
    def errors: String = events.collect { case Event.Err(text) => text }.mkString("\n")
    def output: String = events.collect { case Event.Out(text) => text }.mkString("\n")

  private def command(text: String, warning: String = ""): RawAnswer =
    RawAnswer("command", text, "explained", warning, "")

  private val key = Map("OPENROUTER_API_KEY" -> "sk-or-test", "SHELL" -> "/usr/bin/bash")
  private val linux = "Linux"

  test("--help prints the usage and asks nobody"):
    val io = FakeIo()
    assertEquals(App.run(List("--help"), Map.empty, linux, io), 0)
    assert(io.output.startsWith("Usage: how"))
    assertEquals(io.asked, Vector.empty)

  test("a usage error exits with 2 and asks nobody"):
    val io = FakeIo()
    assertEquals(App.run(List("--colour", "x"), key, linux, io), 2)
    assert(io.errors.contains("Unknown option '--colour'"))
    assertEquals(io.asked, Vector.empty)

  test("a missing key is reported before the question is read"):
    val io = FakeIo(inputs = List("list files"))
    assertEquals(App.run(Nil, Map.empty, linux, io), 1)
    assert(io.errors.contains("export OPENROUTER_API_KEY=<key>"), io.errors)
    assertEquals(io.prompts, Vector.empty)

  test("the missing-key instructions follow the shell the user types in, not --shell"):
    val powerShell = FakeIo()
    assertEquals(App.run(List("--shell", "bash", "list", "files"), Map.empty, "Windows 11", powerShell), 1)
    assert(powerShell.errors.contains("SetEnvironmentVariable"), powerShell.errors)
    val gitBash = FakeIo()
    val env = Map("SHELL" -> "/usr/bin/bash")
    assertEquals(App.run(List("--shell", "powershell", "list", "files"), env, "Windows 11", gitBash), 1)
    assert(gitBash.errors.contains("export OPENROUTER_API_KEY=<key>"), gitBash.errors)

  test("without words the question is read, and a BOM in front is dropped"):
    val io = FakeIo(inputs = List(0xfeff.toChar.toString + "what's in *.log?", "n"))
    assertEquals(App.run(Nil, key, linux, io), 0)
    assertEquals(io.asked, Vector(Event.Asked(Config.DefaultModel, "what's in *.log?")))

  test("without words and without input there is no question"):
    val io = FakeIo()
    assertEquals(App.run(Nil, key, linux, io), 2)
    assert(io.errors.contains("No question given."))
    assertEquals(io.asked, Vector.empty)

  test("a clarification exits with 2 and offers nothing to run"):
    val io = FakeIo(reply = Right(RawAnswer("clarification", "", "", "", "Which files?")))
    assertEquals(App.run(List("do", "it"), key, linux, io), 2)
    assert(io.output.contains("Which files?"))
    assertEquals(io.prompts, Vector.empty)
    assertEquals(io.executed, Vector.empty)

  test("the command comes first; after no nothing runs"):
    val io = FakeIo(inputs = List("n"))
    assertEquals(App.run(List("list", "files"), key, linux, io), 0)
    assertEquals(io.output.linesIterator.next(), "ls")
    assertEquals(io.prompts, Vector("Run it in bash? [y/N] "))
    assertEquals(io.executed, Vector.empty)

  test("after yes the command runs and its exit code is passed on"):
    val io = FakeIo(inputs = List("y"), execution = Right(7))
    assertEquals(App.run(List("list", "files"), key, linux, io), 7)
    assertEquals(io.executed, Vector(Executor.Plan(List("bash"), Some("ls\n"))))

  test("end of input at the confirmation means no"):
    val io = FakeIo()
    assertEquals(App.run(List("list", "files"), key, linux, io), 0)
    assertEquals(io.executed, Vector.empty)

  test("a failed run exits with 1"):
    val io = FakeIo(inputs = List("y"), execution = Left(HowError.Execution("boom")))
    assertEquals(App.run(List("list", "files"), key, linux, io), 1)
    assert(io.errors.contains("Could not run the command: boom"))

  test("on Windows without bash the run is refused with a clear message"):
    val io = FakeIo(inputs = List("y"), existing = _ => false)
    assertEquals(App.run(List("--shell", "bash", "list", "files"), key - "SHELL", "Windows 11", io), 1)
    assert(io.errors.contains("no bash found"))
    assertEquals(io.executed, Vector.empty)

  test("an error from OpenRouter exits with 1"):
    val io = FakeIo(reply = Left(HowError.InvalidApiKey))
    assertEquals(App.run(List("list", "files"), key, linux, io), 1)
    assert(io.errors.contains("HTTP 401"))

  test("a command with control characters is neither shown nor run"):
    val io = FakeIo(inputs = List("y"), reply = Right(command("rm -rf ~/tmp\recho hello")))
    assertEquals(App.run(List("clean", "up"), key, linux, io), 1)
    assertEquals(io.output, "")
    assertEquals(io.executed, Vector.empty)

  test("on Windows without SHELL PowerShell 7 is used if it is there, else Windows PowerShell"):
    val windowsKey = key - "SHELL" + ("PATH" -> "C:\\Program Files\\PowerShell\\7")
    val with7 = FakeIo(inputs = List("y"), existing = _.endsWith("pwsh.exe"))
    App.run(List("list", "files"), windowsKey, "Windows 11", with7)
    assertEquals(with7.executed.map(_.program.head), Vector("pwsh"))
    val without7 = FakeIo(inputs = List("y"), existing = _ => false)
    App.run(List("list", "files"), windowsKey, "Windows 11", without7)
    assertEquals(without7.executed.map(_.program.head), Vector("powershell"))
