package how

import scala.concurrent.duration.*
import scala.util.Try
import sttp.client4.*
import sttp.model.{MediaType, StatusCode}

object OpenRouter:

  val endpoint = uri"https://openrouter.ai/api/v1/chat/completions"

  /** Anfrage als Wert, damit sie sich ohne Netz prüfen lässt. */
  def request(config: Config, systemPrompt: String, question: String): Request[String] =
    basicRequest
      .post(endpoint)
      .auth
      .bearer(config.apiKey)
      .contentType(MediaType.ApplicationJson)
      .header("X-Title", "how")
      .body(ujson.write(body(config.model, systemPrompt, question)))
      .readTimeout(60.seconds)
      .response(asStringAlways)

  def body(model: String, systemPrompt: String, question: String): ujson.Value =
    ujson.Obj(
      "model" -> model,
      "temperature" -> 0.2,
      "max_tokens" -> 1500,
      "messages" -> ujson.Arr(
        ujson.Obj("role" -> "system", "content" -> systemPrompt),
        ujson.Obj("role" -> "user", "content" -> question)
      ),
      "response_format" -> ujson.Obj(
        "type" -> "json_schema",
        "json_schema" -> ujson.Obj("name" -> "how_answer", "strict" -> true, "schema" -> RawAnswer.schema)
      )
    )

  /** Einziger Effekt: die Anfrage senden. Netzwerkfehler werden zu [[HowError.Unreachable]]. */
  def ask(backend: SyncBackend, config: Config, systemPrompt: String, question: String): Either[HowError, RawAnswer] =
    Try(request(config, systemPrompt, question).send(backend)).toEither.left
      .map(e => HowError.Unreachable(HowError.describe(e)))
      .flatMap(response => interpret(response.code, response.body))

  def interpret(status: StatusCode, body: String): Either[HowError, RawAnswer] =
    status.code match
      case 200  => parseCompletion(body)
      case 401  => Left(HowError.InvalidApiKey)
      case 402  => Left(HowError.NoCredits)
      case 429  => Left(HowError.RateLimited)
      case code => Left(HowError.Http(code, errorDetail(body)))

  /** Liest `choices[0].message.content` und darin das JSON der Antwort. Jeder Zugriff auf das JSON läuft in einem
    * `Try`: Eine unerwartete Antwort ist ein [[HowError.InvalidResponse]], nie eine Exception.
    */
  def parseCompletion(body: String): Either[HowError, RawAnswer] =
    def invalid(reason: String) = (_: Throwable) => HowError.InvalidResponse(reason)
    for
      json <- Try(ujson.read(body)).toEither.left.map(invalid("the reply is not JSON"))
      _ <- providerError(json).toLeft(())
      choice <- Try(json("choices")(0).obj).toEither.left.map(invalid("the reply contains no answer"))
      _ <- Either.cond(
        !choice.get("finish_reason").exists(_.strOpt.contains("length")),
        (),
        HowError.InvalidResponse("the answer was cut off")
      )
      content <- Try(choice("message")("content").str).toEither.left.map(invalid("the answer has no text"))
      raw <- Try(upickle.default.read[RawAnswer](unfence(content))).toEither.left
        .map(invalid(s"unexpected format: ${abbreviate(content)}"))
    yield raw

  /** OpenRouter meldet manche Fehler des Anbieters mit Status 200 und einem `error`-Objekt. */
  private def providerError(json: ujson.Value): Option[HowError] =
    json.objOpt
      .flatMap(_.get("error"))
      .map: error =>
        HowError.Upstream(Try(error("message").str).getOrElse(abbreviate(ujson.write(error))))

  /** Manche Modelle setzen trotz JSON Schema Code-Zäune um die Antwort. */
  def unfence(content: String): String =
    val trimmed = content.trim
    if trimmed.startsWith("```") then trimmed.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim
    else trimmed

  private def errorDetail(body: String): String =
    Try(ujson.read(body)("error")("message").str).getOrElse(abbreviate(body))

  private def abbreviate(text: String): String =
    val oneLine = text.replaceAll("\\s+", " ").trim
    if oneLine.length <= 200 then oneLine else oneLine.take(200) + "…"
