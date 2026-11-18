package how

import munit.FunSuite

/** Startet echte Prozesse. Läuft nur, wenn eine bash gefunden wird. */
class ExecutorRun extends FunSuite:

  private val osName = System.getProperty("os.name", "unknown")

  private def runInBash(command: String): Either[HowError, Int] =
    val plan = Executor.plan(Shell.Bash, command, sys.env, osName, Executor.fileExists)
    assume(plan.isRight, s"no bash available: $plan")
    plan.flatMap(Executor.run)

  test("the exit code of the command is passed on"):
    assertEquals(runInBash("exit 3"), Right(3))

  test("a multi-line command runs line by line"):
    assertEquals(runInBash("x=4\nexit $x"), Right(4))

  test("quotes and special characters reach bash unchanged"):
    assertEquals(runInBash("""s='a "b" $c & d'; [ "$s" = 'a "b" $c & d' ] && exit 5"""), Right(5))

  test("a program that does not exist is an error, not an exception"):
    val result = Executor.run(Executor.Plan(List("how-test-no-such-program"), None))
    assert(result.left.exists(_.isInstanceOf[HowError.Execution]), result)

  test("variables of the launcher are not passed on to the command"):
    assertEquals(Executor.LauncherVariables, Set("MSYS_NO_PATHCONV", "HOW_BASH"))
