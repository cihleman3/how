# how

Fragen Sie die Kommandozeile in freien Worten und bekommen Sie einen kopierfertigen Befehl mit kurzer Erklärung:

```
$ how can I find and delete all files with extension '.log' containing the string 'v0.1.0'
grep -rlZF --include='*.log' 'v0.1.0' . | xargs -0 rm -v

grep -r searches recursively in the current directory (.) and -l prints only the names of matching files.
...

Warning: This permanently deletes the matching files. To preview first, run only the part before the pipe: ...

Run it in bash? [y/N]
```

- Der Befehl steht allein und ungefärbt am Anfang, zum Kopieren.
- Die Erklärung kommt in der Sprache der Frage.
- Bei zerstörerischen Befehlen folgt eine Warnung mit einem Vorschlag zur Vorschau.
- Ist die Frage zu unklar, stellt `how` eine Rückfrage und endet (Exit-Code 2).
- Ausgeführt wird nur nach `y` (oder `yes`, `j`, `ja`). Standard ist nein. Nach dem Ausführen gibt `how` den Exit-Code des Befehls zurück.
- Antworten mit Steuerzeichen (z. B. `\r`, ESC) oder unsichtbaren Formatzeichen lehnt `how` ab. Sonst könnte das Terminal einen anderen Befehl anzeigen als den, der läuft.

## Installation

Voraussetzungen: Java 21 und scala-cli.

```sh
bash install.sh                                          # Git Bash, macOS, Linux
powershell -ExecutionPolicy Bypass -File .\install.ps1   # PowerShell
```

Das baut `how.jar` nach `~/.local/share/how`. Die Startskripte landen in `~/.local/bin`: `how` für bash, `how.cmd` für PowerShell und cmd. Der Ordner muss im PATH sein.

Dazu ein API-Schlüssel von OpenRouter in der Umgebung:

```sh
export OPENROUTER_API_KEY=sk-or-...           # bash, z. B. in ~/.bashrc
```

```powershell
[Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', 'sk-or-...', 'User')   # PowerShell, dauerhaft
```

### Für alle Benutzer (Windows)

Voraussetzung für jeden Benutzer: Java 17 oder neuer, über `JAVA_HOME` oder im PATH. Fehlt Java, meldet `how` das.

1. Als normaler Benutzer bauen: `scala-cli --power package . --assembly -o out\how.jar -f` (oder `install.ps1`).
2. In einer PowerShell mit Administratorrechten im Projektordner:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install-all-users.ps1 -KeyFile <Datei mit dem Schlüssel>
   ```

Das kopiert `how.jar`, `how.cmd` und `how` nach `C:\Program Files\how` und nimmt den Ordner in den System-PATH auf. Mit `-KeyFile` wird `OPENROUTER_API_KEY` rechnerweit gesetzt. Alle Benutzer teilen dann den Schlüssel und sein Guthaben, und jeder kann ihn lesen. Ohne `-KeyFile` setzt jeder Benutzer seinen eigenen.

Eine Benutzervariable `OPENROUTER_API_KEY` hat Vorrang vor der rechnerweiten. Der System-PATH kommt vor dem des Benutzers, deshalb gewinnt die Installation für alle Benutzer vor einer eigenen in `~/.local/bin`.

## Shells

| `--shell` | Bedeutung |
|---|---|
| `bash` | bash. Unter Windows Git Bash; `how` startet die bash, aus der es aufgerufen wurde, sonst eine aus `SHELL` oder dem PATH, nie den WSL-Starter aus System32. |
| `powershell` | PowerShell 7 (`pwsh`), wenn installiert, sonst unter Windows Windows PowerShell 5.1. |
| `pwsh` | PowerShell 7. |
| `powershell5` | Windows PowerShell 5.1. Kennt kein `&&` und `\|\|`, `how` schlägt dort `; if ($?) { … }` vor. |

Ohne `--shell` gilt bash, wenn `SHELL` gesetzt ist (Git Bash, macOS, Linux), sonst unter Windows `powershell`.

## Weitere Optionen und Umgebung

| | |
|---|---|
| `--model <id>` | Modell bei OpenRouter. Standard: `HOW_MODEL` oder `anthropic/claude-haiku-5.5`. |
| `--` | Ende der Optionen, für Fragen, die mit `-` beginnen. |
| `NO_COLOR` | Gesetzt: keine Farben. Der Befehl selbst ist ohnehin nie gefärbt. |

Optionen gelten nur vor der Frage. `how what does --force do in git push` funktioniert also.

## Sonderzeichen in der Frage

Die Shell verarbeitet die Wörter, bevor `how` sie sieht:

- bash:
  - Aus `'.log'` wird `.log`.
  - Ein Apostroph wie in `what's` lässt bash auf das Ende des Zitats warten.
  - `*` und `?` werden zu Dateinamen erweitert, wenn passende Dateien existieren.
- PowerShell und cmd: `how.cmd` läuft über cmd, und cmd ersetzt `%VARIABLE%` durch ihren Wert, etwa `%PATH%`.

Für solche Fragen `how` ohne Argumente aufrufen und die Frage eingeben. Dann kommt sie unverändert an, auch mit Umlauten.

## Entwicklung

```sh
scala-cli test . --server=false
scala-cli setup-ide .
scalafmt
```

Aufbau und Konventionen stehen in `CLAUDE.md`. Die Tests rufen nie die echte API auf. Die Tests in `ExecutorRun` starten echte Prozesse und werden übersprungen, wenn keine bash gefunden wird.

### Handtests der Startskripte

Die Startskripte sind nicht automatisch getestet. Nach Änderungen an `launcher/` von Hand prüfen:

- Git Bash: `how list files in /etc` (der Pfad bleibt `/etc`), Ausführen mit `y`.
- PowerShell: `$x = how wie zeige ich die Größe des Ordners Übungen an` (Umlaute richtig), Ausführen mit `y`.
- PowerShell: `how` ohne Argumente mit einer Frage, die `%PATH%` enthält.
