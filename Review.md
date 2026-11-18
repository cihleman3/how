# Review: how

Date: 2026-11-18

Basis:
- **Read:** all sources in `src/` and `test/`, the launchers in `launcher/`, `install.sh` and `install.ps1`.
- **Compiled and tested:** no warnings, 45 tests green.
- **Mutants:** four injected bugs, all caught by the tests (status code mapping, check for an empty command, yes check, WSL filter).
- **Real runs** in Git Bash and PowerShell.
- **Bug hunt:** `/code-review high --max-findings all` with 20 findings. They are merged here with the review's own findings; the source is given in parentheses.
- **Guidelines:** the author's Scala guidelines and the project's `CLAUDE.md`.
- Points confirmed with a script or a real run are marked **(reproduced)**. The others are reasoned from the code.

Note: the code and this review were written in the same session, so the review's own findings are a self-review. `/code-review` ran independently and found most of the points the self-review missed (1, 2, 4, 6, 7, 9, 11).

## Bugs

### 1. The command shown and the command run can differ (`src/Render.scala:17`, `src/Answer.scala:37`) (code-review)

The model's command is printed unfiltered. If it contains control characters, the terminal shows something other than what runs after `y`. Examples are `\r` or ESC sequences. With `rm -rf ~/tmp\r echo hello`, the `\r` overwrites the line: only `echo hello` is visible, but `rm -rf` runs too.

This is the most serious point, because `how` runs commands.

Fix: in `Answer.from`, reject commands with control characters other than `\n` and `\t` (`InvalidResponse`). Add a property: commands with any C0 character or ESC give `Left`.

### 2. Exceptions escape and end `how` with a stack trace (code-review, reproduced)

The `CLAUDE.md` rule "catch exceptions at the edge" is broken in three places:

- **`src/Executor.scala:58`:** `Path.of` runs outside the `Try`. A quoted PATH entry, which is allowed and common on Windows, throws `InvalidPathException: Illegal char <">`. This happens after `y`, even when running in PowerShell, where bash isn't needed at all. Reproduced with `"C:\Program Files\Foo";…`.
- **`src/OpenRouter.scala:58`:** `choice.obj` runs outside the `Try`. The reply `{"choices":[null]}` throws `ujson.Value$InvalidData`. Reproduced.
- **`src/Main.scala:42`:** `Using.resource` rethrows errors from `backend.close()`, and nothing catches them. Not reproduced.

Fix:
- Move the choice of bash into the `Try`, and only for `Shell.Bash`, see point 5.
- Read `finish_reason` with `Try(choice("finish_reason").strOpt)`.
- Wrap `send` as a whole in a `Try`.
- Extend the property "never an exception" with non-objects in `choices`.

### 3. `how.cmd` replaces `%VARIABLE%` in the question (`launcher/how.cmd:3`) (self-review, reproduced)

`%*` makes cmd evaluate the question: `%…%` is replaced and `^` is removed. `how what does %HOWTEST% mean in a batch file` turned into "what does ERSETZT mean …" (the variable's value), and the answer was useless. Every call from PowerShell and cmd is affected, especially questions about environment variables.

Fix: `install.ps1` adds a function to `$PROFILE` for PowerShell, and `how.cmd` stays for cmd only:

```powershell
function how { java -jar "$HOME\.local\share\how\how.jar" @args }
```

This also allows fixing point 8 (output).

### 4. PowerShell 5.1 and 7 are not distinguished (`src/Prompt.scala:12`, `src/Executor.scala:28`) (code-review)

On Windows, `powershell` is always started, which is Windows PowerShell 5.1. That holds even for `--shell pwsh`, which `Shell.parse` accepts as PowerShell.

The prompt, however, explicitly allows `&&` and names no version. Answers like `npm run build && npm test` fail in 5.1 with "The token '&&' is not a valid statement separator". Other syntax that only PowerShell 7 has (`?:`, `??`, `-Parallel`) fails as well.

Fix: two values in `Shell`, `WindowsPowerShell` (5.1) and `PowerShell7` (`pwsh`). The prompt names the version and mentions `&&` only for bash and PowerShell 7. Which version is the default: see Open questions.

### 5. Choosing bash on Windows is unreliable (`src/Executor.scala:37-58`) (self-review and code-review, reproduced)

- **Candidate that does not exist:** If no candidate exists, `run` takes the first one anyway. The message is then something like `Cannot run program "C:\Windows\bash.exe"`. Reproduced.
- **No candidate at all:** Without candidates, `run` falls back to `"bash"`, which is the WSL launcher in System32 that the function is meant to avoid.
- **Forward slashes bypass the filter:** `C:/Windows/System32` is not recognized as WSL, because the filter only checks `\system32\`. Reproduced: candidate `C:/Windows/System32\bash.exe`.

Fix (suggested by code-review, better than guessing): the bash launcher passes the bash that started `how`, e.g. `HOW_BASH="$(cygpath -w "$BASH")"`. Only without it does `how` search. Without a match there is `Left(Execution("no bash found: install Git for Windows or use --shell powershell"))`. The choice becomes a pure function with `exists: String => Boolean`.

### 6. Every command that runs inherits `MSYS_NO_PATHCONV=1` (`launcher/how:7`) (code-review)

The variable is exported and so also applies to the command that `how` starts after `y`. `notepad /c/Users/x/file.txt` then behaves differently from the same line pasted into Git Bash.

Fix: set it only for the Java call, `MSYS_NO_PATHCONV=1 exec java …`, and remove it for the child process in `Executor.run`.

### 7. CRLF in multi-line commands breaks bash (`src/Executor.scala:62`) (code-review)

The script is written exactly as the model returns the command. With `cd /tmp\r\nls`, bash reports `cd: $'/tmp\r': No such file or directory`.

Fix: convert `\r\n` to `\n` before writing. A lone `\r` is already excluded by point 1.

### 8. Non-ASCII characters on Windows (self-review and code-review)

- **Output** (`launcher/how.cmd:3`, reproduced): When PowerShell redirects the output (`how … | clip`, `$x = how …`), Java writes Cp1252 and PowerShell reads cp850. "Übungen" became "�bungen", in the command as well. In the console itself the output is correct, and input through arguments arrived correctly.
- **Interactive input** (`src/Main.scala:47`, code-review, not reproduced because the session has no real console): `StdIn.readLine` reads UTF-8, the console delivers cp850. "lösche" becomes "l�sche", in exactly the mode the README recommends for special characters.

Fix:
- Read through `System.console().reader()` when there is a console. The console knows its character set.
- In the PowerShell function from point 3, set `-Dstdout.encoding` to match `[Console]::OutputEncoding`.

### 9. The provider's error message is lost with HTTP 200 (`src/OpenRouter.scala:46`) (code-review, reproduced)

OpenRouter reports some provider errors with status 200 and `{"error":{"message":…}}`. `how` then only shows "the reply contains no answer". Reproduced.

Fix: in `parseCompletion`, check for `error` first and return `HowError.Http(200, message)`, or a separate case `Provider(message)`.

### 10. Colors in redirected output too (`src/Main.scala:15`) (self-review and code-review)

`interactive` is true as soon as `TERM` is set. Git Bash always sets `TERM`, so `how … > file` gets ANSI codes around the command. Seen in the real runs. Also, from JDK 22 on, `System.console()` is not `null` even with redirection. With Java 21 this does not affect `how` yet.

Fix: never color the command line, only the warning and headings. Then the detection no longer matters. Mention `NO_COLOR` in the README.

### 11. `--model` takes any following word as its value (`src/Cli.scala:41`) (code-review, reproduced)

`how --model --help x` sets the model to "--help". The error only shows up as HTTP 400 from OpenRouter.

Fix: reject values that start with `-` as a usage error. The same applies to `--shell`.

## Design and guidelines

- **`null` comparison** (`src/Main.scala:15`, both): `System.console() != null` contradicts "no `null`; wrap Java APIs in `Option(...)` right away". Better `Option(System.console())`, which can be reused for point 8.
- **Invisible BOM in the source** (`src/Cli.scala:57`, `test/CoreProperties.test.scala:109-110`, both, reproduced: bytes `ef bb bf`): the code reads like `stripPrefix("")`, and an editor that strips BOMs silently disables the function. Write the character so that it is visible in the source.
- **BOM removed in the wrong place** (`src/Executor.scala:16`, `src/Cli.scala:55`, both): the BOM is handled twice separately, and `Executor` depends on `Cli` for it. Better once in a `readLine` wrapper in `Main` that strips the BOM (suggested by code-review). `isYes` and `questionFromInput` then become simple again.
- **Unnecessary work for PowerShell** (`src/Executor.scala:57-62`, both): for PowerShell, `run` still searches for bash, queries the file system for every PATH entry and creates a temporary `.sh` file. This is also the cause of the crash in point 2. Both belong in the `Shell.Bash` branch only.
- **Exceptions described three different ways** (`src/Executor.scala:65`, `src/OpenRouter.scala:41`, `src/Main.scala:40`, code-review): twice `Option(e.getMessage).getOrElse(…)`, once `e.toString`. One helper, e.g. `HowError.describe(e: Throwable)`.
- **Alignment** (`src/Main.scala:35-36`, self-review): the `=>` of the two `case`s are not aligned. Best use scalafmt with a `.scalafmt.conf` in the project.
- **Check against the guidelines:**
  - Met:
    - Indentation syntax; braces only for `collectFirst { case … }`, where they are allowed.
    - Immutable data, no `var`.
    - Errors as values with `HowError`, except for the exceptions in point 2.
    - No `OptionT`/`EitherT`.
    - scala-cli with `project.scala`; versions looked up on Maven Central.
  - cats is not used. That is fine here, because nothing is collected or combined where `Validated` or `traverse` would be clearer.

## Tests

Existing: 45 tests in four suites, MUnit with ScalaCheck, plus example tests with the question from the original request. The generators follow the guidelines:
- They use `Gen.asciiPrintableStr`.
- They deliberately produce nearly equal values.
- The environment keys come from a small pool, so collisions happen.
- Where a value must be guaranteed non-empty, `nonEmptyStringOf` is used.

All four mutants are caught. That the tests still let points 1, 2, 5, 9 and 11 through is due to these gaps:

- **Filter without effect** (`test/OpenRouterTests.test.scala:73`, code-review): `suchThat` checks whether the whole reply is a `RawAnswer`. That is never the case for these generators, so the filter does nothing. Add: `choices` with `null`, numbers and strings, and a 200 reply with `error` (points 2 and 9).
- **Control characters:** no generator produces them. Property: commands with C0 characters or ESC give `Left` (point 1).
- **PATH entries:** the generator has no quotes, no forward slashes and no empty entries. Add them and check that `run` never throws and never chooses WSL (points 2 and 5).
- **Options as values:** `--model` and `--shell` followed by an option give a usage error (point 11).
- **`Main` is untested:** exit codes 0, 1 and 2, the order (key before the interactive question), and that nothing runs after "no". Suggestion: `run` with passed-in functions for input, output, sending and running.
- **`Executor.run` is untested:** an integration test with `exit 3` that expects `Right(3)`, and one with a multi-line CRLF command (point 7). Both with `assume`, only when bash is available.
- **Multi-line commands:** they do not occur in `Gens.answer`, and the property "first line = command" would fail for them. Decide first what they should look like.
- **Launchers:** they are untested, although points 3, 6 and 8 are in them. At least a checklist for manual tests in the README: `%`, `^`, non-ASCII in and out, redirection, running with POSIX paths.

## Open questions

- **PowerShell version:** on Windows, should Windows PowerShell 5.1 be the default because it is always there, or PowerShell 7 when `pwsh` is found? This decides the fix for point 4.
- **Control characters:** should `how` reject commands with control characters (safer) or make them visible, e.g. as `\r` (more informative)?
- **Exit code for a clarification:** at 2 it is the same as for usage errors. Should a script be able to tell them apart, e.g. with 3 for a clarification?
- **`#< CLIXML`:** when running in PowerShell with redirected error output, XML fragments appear. This does not happen in a terminal. Should it be handled?

## Priority

1. **Point 1** (control characters): the only place where `how` can run something other than what the user confirmed.
2. **Point 2** (crashes): they break the project's basic rule. Rebuilding `Executor.run` also removes the unnecessary work for PowerShell.
3. **Points 3, 6 and 8** (launchers): fix them together, with a PowerShell function, `MSYS_NO_PATHCONV` for Java only, and the console's character set.
4. **Points 4 and 5** (the right shell): point 4 only after the open question is settled.
5. **Points 7, 9 and 11** and the test gaps: write the new properties first; they must fail on today's bugs.
6. **Design points and point 10:** cleanup.

## Status of the fixes (2026-11-18)

The author's decisions:
- PowerShell 7 if available.
- Reject commands with control characters.
- The name `how` everywhere.
- For PowerShell, `how.cmd` stays; the execution policy is not changed.

Approach: tests first. They grew from 45 to 78. Then eight mutants with the old bugs, all caught.

| Point | Status |
|---|---|
| 1 Control characters | Fixed. `Answer.from` rejects control characters in all texts, also at the edges before `trim`, and formatting characters in the command. |
| 2 Exceptions | Fixed. JSON access and `Path.of` run in `Try`, and so does sending, including `close`. `HowError.describe` uses the innermost message (found along the way: sttp wraps "connection refused" in a generic message). |
| 3 `%` in `how.cmd` | Stays as a documented limitation (README, help, comment in `how.cmd`). Workaround: `how` without arguments, checked in a real run with `%PATH%`. |
| 4 PowerShell 5.1/7 | Fixed. `Shell` knows `WindowsPowerShell` and `PowerShell7`. `powershell` means 7 if available. The prompt forbids `&&` for 5.1; real run: `npm run build; if ($?) { npm test }`. Only checked with tests, as this computer has no `pwsh`. |
| 5 Choosing bash | Fixed. Order: `HOW_BASH` (from the launcher), `SHELL`, PATH. Paths are cleaned (quotes, `/`), WSL launchers are skipped. Without a match there is a clear message. |
| 6 `MSYS_NO_PATHCONV` | Fixed. Set only for Java and removed when running a command, as is `HOW_BASH`. |
| 7 CRLF | Fixed. `Answer.from` turns `\r\n` into `\n`. |
| 8 Non-ASCII characters | Output fixed: `how.cmd` passes the console's code page to Java, checked in a real run with redirected output. Input now goes through `System.console().reader()`; still to be checked by hand in a real console. |
| 9 Errors with HTTP 200 | Fixed. New case `HowError.Upstream` with the provider's message. |
| 10 Colors | Fixed. The command is never colored. |
| 11 Option as value | Fixed, for `--model` and `--shell`. |
| Design | All points fixed: `Option(System.console())`; BOM as `0xfeff.toChar` in `Input.clean`, applied in `App` to every input; for PowerShell neither a bash search nor a script file; one helper for exceptions; scalafmt with `.scalafmt.conf`. |
| Tests | All gaps closed: `App` through the `Io` interface, `Executor.run` with real processes, control and formatting characters, unusual PATH entries, JSON values of every kind in `choices`, multi-line commands. The launchers have a checklist for manual tests in the README. |

Still open:
- Exit code 2 for a clarification: unchanged.
- `#< CLIXML`: still appears with redirected error output.
