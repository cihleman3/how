package how

import munit.FunSuite
import sttp.client4.testing.SyncBackendStub
import sttp.model.StatusCode

/** The cases from the original request, with fixed answers instead of the real API. */
class Examples extends FunSuite:

  private val args =
    "can I find and delete all files with extension .log containing the string v0.1.0".split(' ').toList

  private def reply(raw: RawAnswer): String =
    ujson.write(
      ujson.Obj("choices" -> ujson.Arr(ujson.Obj("message" -> ujson.Obj("content" -> upickle.default.write(raw)))))
    )

  private def ask(raw: RawAnswer): Either[HowError, Answer] =
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(reply(raw), StatusCode.Ok)
    for
      cli <- Cli.parse(args)
      config <- Config.fromEnv(Map("OPENROUTER_API_KEY" -> "sk-or-test"), cli.model, Shell.Bash)
      question <- cli.question.toRight(HowError.Usage("no question"))
      raw <- OpenRouter.ask(backend, config, Prompt.system(Shell.Bash, "Linux"), question)
      answer <- Answer.from(raw)
    yield answer

  test("the example from the task gives a command, an explanation and a warning"):
    val command = """grep -rlZ --include='*.log' 'v0.1.0' . | xargs -0 rm --"""
    val raw = RawAnswer(
      "command",
      command,
      "grep -r searches recursively, -l prints only file names, -Z ends them with NUL.\nxargs -0 rm deletes them.",
      "This deletes files. Run it without '| xargs -0 rm --' first to see which ones.",
      ""
    )
    val answer = ask(raw)
    assertEquals(answer.map(a => Render.answer(a, color = false).linesIterator.next()), Right(command))
    assert(answer.exists:
      case Answer.Command(_, _, warning) => warning.isDefined
      case _                             => false)

  test("the question reaches the model as one sentence"):
    assertEquals(
      Cli.parse(args).map(_.question),
      Right(Some("can I find and delete all files with extension .log containing the string v0.1.0"))
    )

  test("an unclear question gives a question back"):
    val answer = ask(RawAnswer("clarification", "", "", "", "Which files do you mean?"))
    assertEquals(answer, Right(Answer.Clarification("Which files do you mean?")))

  test("the system prompt names the shell and the operating system"):
    val prompt = Prompt.system(Shell.WindowsPowerShell, "Windows 11")
    assert(prompt.contains("Windows PowerShell 5.1 on Windows 11"))
