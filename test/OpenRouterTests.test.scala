package how

import java.net.ConnectException
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*
import scala.util.Try
import sttp.client4.testing.SyncBackendStub
import sttp.model.{Header, StatusCode}

class OpenRouterTests extends ScalaCheckSuite:

  private val config = Config("sk-or-test", "some/model")

  /** A reply from OpenRouter in the format of the Chat Completions API. */
  private def completion(content: String, finishReason: String = "stop"): String =
    ujson.write(
      ujson.Obj(
        "choices" -> ujson.Arr(
          ujson.Obj(
            "finish_reason" -> finishReason,
            "message" -> ujson.Obj("role" -> "assistant", "content" -> content)
          )
        )
      )
    )

  private def requestBody(request: sttp.client4.Request[String]): ujson.Value =
    ujson.read(request.body.show.stripPrefix("string: "))

  // Request

  property("the request carries key, model, prompt and question unchanged"):
    forAllNoShrink(Gens.nonBlank, Gens.nonBlank, Gens.text, Gens.text): (key, model, prompt, question) =>
      val request = OpenRouter.request(Config(key, model), prompt, question)
      val body = requestBody(request)
      assertEquals(request.uri, OpenRouter.endpoint)
      assertEquals(request.header("Authorization"), Some(s"Bearer $key"))
      assertEquals(body("model").str, model)
      assertEquals(body("messages")(0)("content").str, prompt)
      assertEquals(body("messages")(1)("content").str, question)

  test("the request asks for the answer schema in strict mode"):
    val format = requestBody(OpenRouter.request(config, "p", "q"))("response_format")
    assertEquals(format("type").str, "json_schema")
    assertEquals(format("json_schema")("strict").bool, true)
    assertEquals(format("json_schema")("schema"), RawAnswer.schema)

  test("the schema lists exactly the fields of RawAnswer, all required"):
    val fields = RawAnswer.schema("properties").obj.keySet
    assertEquals(fields, Set("kind", "command", "explanation", "warning", "question"))
    assertEquals(RawAnswer.schema("required").arr.map(_.str).toSet, fields)

  // Response

  property("an answer comes back as it was sent, also inside code fences"):
    forAllNoShrink(Gens.rawAnswer, Gen.oneOf("", "```json\n", "```\n"), Gens.blank): (raw, fence, blank) =>
      val json = upickle.default.write(raw)
      val content = if fence.isEmpty then s"$blank$json$blank" else s"$fence$json\n```"
      assertEquals(OpenRouter.interpret(StatusCode.Ok, completion(content)), Right(raw))

  test("a cut-off answer is an error"):
    val content = upickle.default.write(RawAnswer("command", "ls", "lists", "", ""))
    assertEquals(
      OpenRouter.interpret(StatusCode.Ok, completion(content, finishReason = "length")),
      Left(HowError.InvalidResponse("the answer was cut off"))
    )

  /** JSON values of every kind, to put where an object is expected. */
  private val anyJson: Gen[ujson.Value] = Gen.oneOf(
    Gen.const(ujson.Null),
    Gen.choose(-5, 5).map(n => ujson.Num(n)),
    Gens.text.map(ujson.Str(_)),
    Gen.const(ujson.Bool(true)),
    Gen.const(ujson.Arr()),
    Gen.const(ujson.Obj()),
    Gen.const(ujson.Obj("message" -> ujson.Null)),
    Gen.const(ujson.Obj("message" -> ujson.Obj("content" -> 1))),
    Gen.const(ujson.Obj("finish_reason" -> 1, "message" -> ujson.Obj("content" -> "x")))
  )

  property("a 200 reply that is not the expected JSON is an invalid response, never an exception"):
    val replies = Gen.oneOf(
      Gens.text,
      Gens.text.map(t => completion(t)),
      anyJson.map(v => ujson.write(ujson.Obj("choices" -> ujson.Arr(v)))),
      anyJson.map(v => ujson.write(ujson.Obj("choices" -> v))),
      anyJson.map(ujson.write(_))
    )
    forAllNoShrink(replies): reply =>
      val result = Try(OpenRouter.interpret(StatusCode.Ok, reply))
      assert(result.isSuccess, s"threw for $reply: $result")
      assert(result.get.left.exists(_.isInstanceOf[HowError.InvalidResponse]), s"$reply -> ${result.get}")

  property("a 200 reply with an error object keeps the provider's message"):
    forAllNoShrink(Gens.nonBlank): message =>
      val body = ujson.write(ujson.Obj("error" -> ujson.Obj("message" -> message, "code" -> 502)))
      assertEquals(OpenRouter.interpret(StatusCode.Ok, body), Left(HowError.Upstream(message)))

  test("status codes are mapped to errors"):
    assertEquals(OpenRouter.interpret(StatusCode(401), ""), Left(HowError.InvalidApiKey))
    assertEquals(OpenRouter.interpret(StatusCode(402), ""), Left(HowError.NoCredits))
    assertEquals(OpenRouter.interpret(StatusCode(429), ""), Left(HowError.RateLimited))

  property("other status codes keep the code and OpenRouter's message"):
    val codes = Gen.choose(300, 599).suchThat(c => !Set(401, 402, 429).contains(c))
    forAllNoShrink(codes, Gens.text): (code, message) =>
      val body = ujson.write(ujson.Obj("error" -> ujson.Obj("message" -> message, "code" -> code)))
      assertEquals(OpenRouter.interpret(StatusCode(code), body), Left(HowError.Http(code, message)))

  // Sending with a stub

  test("ask sends the request and reads the answer"):
    val raw = RawAnswer("command", "ls -la", "lists all files", "", "")
    val backend = SyncBackendStub
      .whenRequestMatches(_.headers.contains(Header("Authorization", "Bearer sk-or-test")))
      .thenRespondAdjust(completion(upickle.default.write(raw)), StatusCode.Ok)
    assertEquals(OpenRouter.ask(backend, config, "p", "q"), Right(raw))

  test("a network failure becomes Unreachable"):
    val backend = SyncBackendStub.whenAnyRequest.thenThrow(ConnectException("connection refused"))
    assertEquals(OpenRouter.ask(backend, config, "p", "q"), Left(HowError.Unreachable("connection refused")))
