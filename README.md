# how

Ask the command line in plain words and get a command ready to copy and paste, with a short explanation:

```
$ how can I find and delete all files with extension '.log' containing the string 'v0.1.0'
grep -rlZF --include='*.log' 'v0.1.0' . | xargs -0 rm -v

grep -r searches recursively in the current directory (.) and -l prints only the names of matching files.
...

Warning: This permanently deletes the matching files. To preview first, run only the part before the pipe: ...

Run it in bash? [y/N]
```

- The command comes first, on its own and without color, so it is easy to copy.
- The explanation is in the language of the question.
- Destructive commands come with a warning and a suggestion for previewing the effect.
- If the question is too vague, `how` asks back and exits (exit code 2).
- The command runs only after `y` (or `yes`, `j`, `ja`); the default is no. After running it, `how` returns the command's exit code.
- `how` rejects answers that contain control characters (such as `\r` or ESC) or invisible formatting characters. Otherwise the terminal could show a different command from the one that runs.

## Installation

### From a release (needs only Java)

Requirement: Java 17 or newer.

1. Download `how-<version>.zip` from the [latest release](https://github.com/cihleman3/how/releases/latest) and unpack it.
2. In the unpacked folder:

   ```sh
   bash install.sh                                          # Git Bash, macOS, Linux
   powershell -ExecutionPolicy Bypass -File .\install.ps1   # PowerShell
   ```

### From source

Requirements: Java 17 or newer, and [scala-cli](https://scala-cli.virtuslab.org/). In a clone of this repository, run the same install script as above. It builds `how.jar` first.

### Where it goes

The install scripts copy `how.jar` into `~/.local/share/how` and the launchers into `~/.local/bin`: `how` for bash, `how.cmd` for PowerShell and cmd. That folder must be on your PATH. On macOS you may need to add `export PATH="$HOME/.local/bin:$PATH"` to `~/.zshrc`.

You also need an [OpenRouter](https://openrouter.ai/keys) API key in your environment:

```sh
export OPENROUTER_API_KEY=sk-or-...           # bash, e.g. in ~/.bashrc
```

```powershell
[Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', 'sk-or-...', 'User')   # PowerShell, permanent
```

### For all users (Windows)

Every user needs Java 17 or newer, via `JAVA_HOME` or on the PATH. Without Java, `how` says so.

1. A release download already contains `how.jar`. From a git clone, build it first as a normal user: `scala-cli --power package . --assembly -o out\how.jar -f`.
2. In the unpacked release folder or the clone, in a PowerShell with administrator rights:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install-all-users.ps1 -KeyFile <file containing the key>
   ```

This copies `how.jar`, `how.cmd` and `how` to `C:\Program Files\how` and adds that folder to the system PATH. With `-KeyFile`, `OPENROUTER_API_KEY` is set machine-wide: all users then share the key and its credit, and every user can read it. Without `-KeyFile`, each user sets their own.

A user's own `OPENROUTER_API_KEY` takes precedence over the machine-wide one. The system PATH comes before the user's PATH, so the all-users installation wins over a personal one in `~/.local/bin`.

## Shells

| `--shell` | Meaning |
|---|---|
| `bash` | bash. On Windows this is Git Bash: `how` uses the bash it was started from, otherwise one from `SHELL` or the PATH, never the WSL launcher in System32. |
| `powershell` | PowerShell 7 (`pwsh`) if installed, otherwise Windows PowerShell 5.1 on Windows. |
| `pwsh` | PowerShell 7. |
| `powershell5` | Windows PowerShell 5.1. It has no `&&` and `\|\|`, so `how` suggests `; if ($?) { … }` instead. |

Without `--shell`, `how` uses bash if `SHELL` is set (Git Bash, macOS, Linux), otherwise `powershell` on Windows.

## Other options and environment

| | |
|---|---|
| `--model <id>` | OpenRouter model. Default: `HOW_MODEL` or `anthropic/claude-haiku-5.5`. |
| `--` | End of options, for questions that start with `-`. |
| `NO_COLOR` | If set: no colors. The command itself is never colored anyway. |

Options are only recognized before the question, so `how what does --force do in git push` works.

## Special characters in the question

The shell processes the words before `how` sees them:

- bash:
  - `'.log'` becomes `.log`.
  - An apostrophe, as in `what's`, makes bash wait for the closing quote.
  - `*` and `?` expand to file names if matching files exist.
- PowerShell and cmd: `how.cmd` runs through cmd, and cmd replaces `%VARIABLE%` with its value, e.g. `%PATH%`.

For such questions, run `how` without arguments and type the question. It then arrives unchanged, including non-ASCII characters.

## Development

```sh
scala-cli test .
scala-cli setup-ide .
scalafmt
```

Structure and conventions are described in `CLAUDE.md` (in German). The tests never call the real API. The tests in `ExecutorRun` start real processes and are skipped if no bash is found.

### Manual tests of the launchers

The launchers are not tested automatically. After changing anything in `launcher/`, check by hand:

- Git Bash: `how list files in /etc` (the path stays `/etc`), and run the command with `y`.
- PowerShell: `$x = how wie zeige ich die Größe des Ordners Übungen an` (non-ASCII characters survive redirected output), and run the command with `y`.
- PowerShell: `how` without arguments, with a question containing `%PATH%`.
