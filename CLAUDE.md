# how

Command-line tool: `how <question in plain words>` returns a shell command ready to copy and paste, with a short explanation. The model is called through OpenRouter.

The author's global Scala guidelines apply. In addition, this project follows these rules:

## Structure

- Pure core in package `how`: `Cli`, `Shell`, `Prompt`, `Answer`, `Render`, `Config`, `Input`, `Executables`, `App` and the pure parts of `OpenRouter` and `Executor`. No input or output, no environment variables, no network, no file system. Everything from outside is passed in as a parameter (e.g. `env: Map[String, String]`, `osName: String`, `exists: String => Boolean`).
- `App.run` is the flow of one call. It gets all effects through the `Io` interface; `Main.RealIo` provides the real ones, `AppTests` its own.
- Effects only at the edge:
  - `Main.RealIo` for the console and input,
  - `OpenRouter.ask` for HTTP,
  - `Executor.run` and `Executor.fileExists` for processes and the file system.
  Each effectful function is thin and calls pure functions that are tested. What gets started is planned as a value by `Executor.plan`.
- Errors as values: `enum HowError`. Exceptions from sttp, ujson and processes are caught at the edge with `Try` and turned into a `HowError` with `HowError.describe`. They must not escape; every JSON access and every `Path.of` runs inside a `Try` as well.
- Answers from the model are not trusted: `Answer.from` rejects control and formatting characters, so that what is shown is what runs.
- Formatting with scalafmt (`.scalafmt.conf`).
- No Unicode escape sequences for special characters (such as U+FEFF or ESC) in the source: tools may write them as real, invisible characters. Write special characters as numbers instead (`0xfeff.toChar`, `27.toChar`).
- The output is the product: the command stands alone on its line, without prefix, so it can be copied.
- User interface texts in English. The model's explanation is in the language of the question.

## Security

- The API key comes only from the environment variable `OPENROUTER_API_KEY`. Never in code, tests, logs or error messages.
- A command runs only after explicit confirmation (default: no).

## Tests

- MUnit with ScalaCheck properties, plus example tests with the question from the original request.
- Tests never call the real API: requests are checked as values, responses are simulated with `SyncBackendStub`.
- Run the tests with `scala-cli test .` (add `--server=false` if the Bloop server cannot start).

## Tools

- Use Metals (MCP) when available: for navigation, references and diagnostics.
