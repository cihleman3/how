package how

import java.io.{BufferedReader, InputStreamReader}
import scala.concurrent.duration.*
import scala.util.{Try, Using}
import sttp.client4.{BackendOptions, DefaultSyncBackend}

/** Rand des Programms: die echten Effekte für [[App]]. */
object Main:

  def main(args: Array[String]): Unit =
    sys.exit(App.run(args.toList, sys.env, System.getProperty("os.name", "unknown"), RealIo(sys.env)))

  private final class RealIo(env: Map[String, String]) extends Io:

    private val console = Option(System.console())

    /** Eingaben über die Konsole lesen, wenn es eine gibt: Sie kennt ihren Zeichensatz (unter Windows meist cp850),
      * `System.in` würde als UTF-8 gelesen und Umlaute zerstören. Ohne Konsole (Pipe, mintty) bleibt `System.in`.
      */
    private val input: BufferedReader =
      console.map(c => BufferedReader(c.reader())).getOrElse(BufferedReader(InputStreamReader(System.in)))

    /** Git Bash (mintty) ist für Java keine Konsole, setzt aber `TERM`. */
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
