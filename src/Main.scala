package how

import java.io.{BufferedReader, InputStreamReader}
import scala.concurrent.duration.*
import scala.util.{Try, Using}
import sttp.client4.{BackendOptions, DefaultSyncBackend}

/** Edge of the program: the real effects for [[App]]. */
object Main:

  def main(args: Array[String]): Unit =
    sys.exit(App.run(args.toList, sys.env, System.getProperty("os.name", "unknown"), RealIo(sys.env)))

  private final class RealIo(env: Map[String, String]) extends Io:

    private val console = Option(System.console())

    /** Reads input through the console if there is one: it knows its character set (on Windows usually cp850), whereas
      * `System.in` would be read as UTF-8 and break non-ASCII characters. Without a console (pipe, mintty), `System.in`
      * is used.
      */
    private val input: BufferedReader =
      console.map(c => BufferedReader(c.reader())).getOrElse(BufferedReader(InputStreamReader(System.in)))

    /** Git Bash (mintty) is no console for Java, but it sets `TERM`. */
    val interactive: Boolean = console.isDefined || env.get("TERM").exists(_.nonEmpty)

    def readLine(prompt: String): Option[String] =
      System.err.print(prompt)
      System.err.flush()
      Try(Option(input.readLine())).toOption.flatten

    def out(text: String): Unit = println(text)

    def err(text: String): Unit = System.err.println(text)

    def ask(config: Config, systemPrompt: String, question: String): Either[HowError, RawAnswer] =
      Try:
        Using.resource(DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))): backend =>
          OpenRouter.ask(backend, config, systemPrompt, question)
      .toEither.left.map(e => HowError.Unreachable(HowError.describe(e))).flatten

    def exists(path: String): Boolean = Executor.fileExists(path)

    def execute(plan: Executor.Plan): Either[HowError, Int] = Executor.run(plan)
