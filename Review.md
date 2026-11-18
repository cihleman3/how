# Review: how (`C:\Users\sandbox\scala\how`)

Stand: 18.11.2026

Grundlage:
- **Gelesen:** alle Quellen in `src/` und `test/`, die Startskripte in `launcher/`, `install.sh` und `install.ps1`.
- **Kompiliert und getestet:** ohne Warnungen, 45 Tests grün.
- **Mutanten:** vier eingebaute Fehler, alle von den Tests erkannt (Statuscode-Zuordnung, Prüfung auf leeren Befehl, Ja-Prüfung, WSL-Filter).
- **Echte Läufe** in Git Bash und PowerShell.
- **Fehlersuche:** `/code-review high --max-findings all` mit 20 Befunden. Sie sind hier mit den eigenen Befunden zusammengeführt, die Herkunft steht jeweils in Klammern.
- **Richtlinien:** `~/.claude/scala-richtlinien.md` und die `CLAUDE.md` des Projekts.
- Mit einem Skript oder einem echten Lauf bestätigte Punkte sind mit **(nachgestellt)** markiert. Die übrigen sind aus dem Code begründet.

Hinweis: Der Code und die eigene Prüfung stammen aus derselben Sitzung. `/code-review` lief unabhängig davon und hat die meisten Punkte gefunden, die bei der Selbstprüfung fehlten (1, 2, 4, 6, 7, 9, 11).

## Fehler

### 1. Angezeigter und ausgeführter Befehl können sich unterscheiden (`src/Render.scala:17`, `src/Answer.scala:37`) (code-review)

Der Befehl des Modells wird ungefiltert ausgegeben. Enthält er Steuerzeichen, zeigt das Terminal etwas anderes, als nach `y` läuft. Beispiele sind `\r` oder ESC-Sequenzen. Bei `rm -rf ~/tmp\r echo hello` überschreibt `\r` die Zeile. Sichtbar ist nur `echo hello`, ausgeführt wird auch `rm -rf`.

Das ist der schwerste Punkt, weil `how` Befehle ausführt.

Lösung: In `Answer.from` Befehle mit Steuerzeichen außer `\n` und `\t` ablehnen (`InvalidResponse`). Dazu eine Eigenschaft: Befehle mit beliebigen C0-Zeichen und ESC ergeben `Left`.

### 2. Exceptions dringen nach außen und beenden `how` mit Stacktrace (code-review, nachgestellt)

Die Regel aus der `CLAUDE.md` „Exceptions … am Rand fangen“ ist an drei Stellen verletzt:

- **`src/Executor.scala:58`:** `Path.of` läuft außerhalb des `Try`. Ein PATH-Eintrag in Anführungszeichen, unter Windows erlaubt und häufig, wirft `InvalidPathException: Illegal char <">`. Das passiert nach dem `y`, und zwar auch beim Ausführen in PowerShell, wo bash gar nicht gebraucht wird. Nachgestellt mit `"C:\Program Files\Foo";…`.
- **`src/OpenRouter.scala:58`:** `choice.obj` läuft außerhalb des `Try`. Die Antwort `{"choices":[null]}` wirft `ujson.Value$InvalidData`. Nachgestellt.
- **`src/Main.scala:42`:** `Using.resource` wirft Fehler aus `backend.close()` weiter, niemand fängt sie. Nicht nachgestellt.

Lösung:
- Die Auswahl der bash in das `Try` holen, und zwar nur für `Shell.Bash`, siehe Punkt 5.
- `finish_reason` mit `Try(choice("finish_reason").strOpt)` lesen.
- `send` als Ganzes in ein `Try` packen.
- Die Eigenschaft „never an exception“ um Nicht-Objekte in `choices` erweitern.

### 3. `how.cmd` ersetzt `%VARIABLE%` in der Frage (`launcher/how.cmd:3`) (eigene Prüfung, nachgestellt)

`%*` lässt cmd die Frage auswerten: `%…%` wird ersetzt, `^` entfernt. Aus `how what does %HOWTEST% mean in a batch file` wurde „what does ERSETZT mean …“, und die Antwort war unbrauchbar. Betroffen ist jeder Aufruf aus PowerShell und cmd, gerade bei Fragen zu Umgebungsvariablen.

Lösung: `install.ps1` trägt für PowerShell eine Funktion in `$PROFILE` ein, `how.cmd` bleibt nur für cmd:

```powershell
function how { java -jar "$HOME\.local\share\how\how.jar" @args }
```

Damit lässt sich auch Punkt 8 (Ausgabe) lösen.

### 4. PowerShell 5.1 und 7 werden nicht unterschieden (`src/Prompt.scala:12`, `src/Executor.scala:28`) (code-review)

Unter Windows wird immer `powershell` gestartet, also Windows PowerShell 5.1. Das gilt auch bei `--shell pwsh`, das `Shell.parse` als PowerShell annimmt.

Der Prompt erlaubt dagegen ausdrücklich `&&` und nennt keine Version. Antworten wie `npm run build && npm test` scheitern in 5.1 mit „The token '&&' is not a valid statement separator“. Andere Syntax nur für PowerShell 7 (`?:`, `??`, `-Parallel`) scheitert ebenso.

Lösung: zwei Werte in `Shell`, `WindowsPowerShell` (5.1) und `PowerShell7` (`pwsh`). Der Prompt nennt die Version, und `&&` wird nur für bash und PowerShell 7 erwähnt. Welche Version die Vorgabe ist, siehe Offene Fragen.

### 5. Die Wahl der bash unter Windows ist unzuverlässig (`src/Executor.scala:37-58`) (eigene Prüfung und code-review, nachgestellt)

- **Nicht existierender Kandidat:** Gibt es keinen existierenden Kandidaten, nimmt `run` den ersten nicht existierenden. Die Meldung lautet dann etwa `Cannot run program "C:\Windows\bash.exe"`. Nachgestellt.
- **Gar kein Kandidat:** Ohne Kandidaten fällt `run` auf `"bash"` zurück, also auf den WSL-Starter aus System32, den die Funktion gerade vermeiden soll.
- **Schrägstriche umgehen den Filter:** `C:/Windows/System32` wird nicht als WSL erkannt, der Filter prüft nur `\system32\`. Nachgestellt: Kandidat `C:/Windows/System32\bash.exe`.

Lösung (Vorschlag aus code-review, besser als Raten): Das bash-Startskript übergibt die bash, die `how` gestartet hat, etwa `HOW_BASH="$(cygpath -w "$BASH")"`. Nur ohne diese Angabe wird gesucht. Ohne Treffer gibt es `Left(Execution("no bash found: install Git for Windows or use --shell powershell"))`. Die Auswahl wird eine reine Funktion mit `exists: String => Boolean`.

### 6. `MSYS_NO_PATHCONV=1` erbt jeder ausgeführte Befehl (`launcher/how:7`) (code-review)

Die Variable wird exportiert und gilt damit auch für den Befehl, den `how` nach `y` startet. `notepad /c/Users/x/datei.txt` verhält sich dann anders, als wenn der Nutzer dieselbe Zeile in Git Bash einfügt.

Lösung: nur für den Java-Aufruf setzen, `MSYS_NO_PATHCONV=1 exec java …`, und in `Executor.run` für den Kindprozess entfernen.

### 7. CRLF in mehrzeiligen Befehlen bricht bash (`src/Executor.scala:62`) (code-review)

Das Skript wird geschrieben, wie das Modell den Befehl liefert. Bei `cd /tmp\r\nls` meldet bash `cd: $'/tmp\r': No such file or directory`.

Lösung: vor dem Schreiben `\r\n` in `\n` umwandeln. Ein `\r` allein ist durch Punkt 1 bereits ausgeschlossen.

### 8. Umlaute unter Windows (eigene Prüfung und code-review)

- **Ausgabe** (`launcher/how.cmd:3`, nachgestellt): Leitet PowerShell die Ausgabe um (`how … | clip`, `$x = how …`), schreibt Java in Cp1252, PowerShell liest cp850. Aus „Übungen“ wurde „�bungen“, auch im Befehl. In der Konsole selbst ist die Ausgabe richtig, und die Eingabe über Argumente kam korrekt an.
- **Interaktive Eingabe** (`src/Main.scala:47`, code-review, nicht nachgestellt, weil die Sitzung keine echte Konsole hat): `StdIn.readLine` liest UTF-8, die Konsole liefert cp850. Aus `lösche` wird `l�sche`, und das ausgerechnet in dem Modus, den die README für Sonderzeichen empfiehlt.

Lösung:
- Lesen über `System.console().reader()`, wenn eine Konsole da ist. Die Konsole kennt ihren Zeichensatz.
- In der PowerShell-Funktion aus Punkt 3 `-Dstdout.encoding` passend zu `[Console]::OutputEncoding` setzen.

### 9. Fehlermeldung des Anbieters geht bei HTTP 200 verloren (`src/OpenRouter.scala:46`) (code-review, nachgestellt)

OpenRouter meldet manche Fehler des Anbieters mit Status 200 und `{"error":{"message":…}}`. `how` zeigt dann nur „the reply contains no answer“. Nachgestellt.

Lösung: In `parseCompletion` zuerst auf `error` prüfen und `HowError.Http(200, message)` liefern, oder einen eigenen Fall `Provider(message)`.

### 10. Farben auch in umgeleiteter Ausgabe (`src/Main.scala:15`) (eigene Prüfung und code-review)

`interactive` ist wahr, sobald `TERM` gesetzt ist. Git Bash setzt `TERM` immer, deshalb bekommt `how … > datei` ANSI-Codes um den Befehl. In den echten Läufen gesehen. Ab JDK 22 ist zudem `System.console()` auch bei Umleitung nicht `null`. Mit Java 21 betrifft das `how` noch nicht.

Lösung: Die Befehlszeile gar nicht färben, nur Warnung und Überschriften. Dann ist die Erkennung nicht mehr kritisch. `NO_COLOR` in der README nennen.

### 11. `--model` nimmt jedes folgende Wort als Wert (`src/Cli.scala:41`) (code-review, nachgestellt)

`how --model --help x` setzt das Modell auf „--help“. Der Fehler kommt erst als HTTP 400 von OpenRouter.

Lösung: Werte, die mit `-` beginnen, als Bedienfehler ablehnen. Gleiches gilt für `--shell`.

## Design und Richtlinien

- **`null`-Vergleich** (`src/Main.scala:15`, beide): `System.console() != null` widerspricht „kein `null`, Java-APIs sofort mit `Option(...)` einpacken“. Besser `Option(System.console())`, das gleich für Punkt 8 weiterverwendet wird.
- **Unsichtbares BOM im Quelltext** (`src/Cli.scala:57`, `test/CoreProperties.test.scala:109-110`, beide, nachgestellt: Bytes `ef bb bf`): Der Code liest sich wie `stripPrefix("")`, und ein Editor, der BOMs entfernt, macht die Funktion still wirkungslos. Immer `"\uFEFF"` schreiben.
- **BOM an der falschen Stelle entfernt** (`src/Executor.scala:16`, `src/Cli.scala:55`, beide): Das BOM wird zweimal einzeln behandelt, und `Executor` hängt dafür von `Cli` ab. Besser einmal in einer `readLine`-Hülle in `Main`, die das BOM entfernt (Vorschlag aus code-review). `isYes` und `questionFromInput` werden dann wieder einfach.
- **Unnötige Arbeit bei PowerShell** (`src/Executor.scala:57-62`, beide): Für PowerShell sucht `run` trotzdem nach bash, fragt das Dateisystem für jeden PATH-Eintrag ab und legt eine temporäre `.sh`-Datei an. Das ist auch die Ursache des Absturzes in Punkt 2. Beides gehört nur in den Zweig `Shell.Bash`.
- **Exceptions dreifach unterschiedlich beschrieben** (`src/Executor.scala:65`, `src/OpenRouter.scala:41`, `src/Main.scala:40`, code-review): zweimal `Option(e.getMessage).getOrElse(…)`, einmal `e.toString`. Eine Hilfsfunktion, z. B. `HowError.describe(e: Throwable)`.
- **Ausrichtung** (`src/Main.scala:35-36`, eigene Prüfung): Die `=>` der beiden `case` stehen nicht untereinander. Am besten scalafmt mit einer `.scalafmt.conf` im Projekt.
- **Abgleich mit den Richtlinien:**
  - Erfüllt:
    - Einrückungssyntax; geschweifte Klammern nur bei `collectFirst { case … }`, wo es erlaubt ist.
    - Unveränderliche Daten, kein `var`.
    - Fehler als Wert mit `HowError`, außer den Ausnahmen aus Punkt 2.
    - Kein `OptionT`/`EitherT`.
    - scala-cli mit `project.scala`; Versionen auf Maven Central nachgesehen.
  - cats wird nicht verwendet. Das ist hier in Ordnung, weil nichts gesammelt oder kombiniert wird, wo `Validated` oder `traverse` klarer wären.

## Tests

Bestand: 45 Tests in vier Suiten, MUnit mit ScalaCheck, dazu Beispieltests mit der Frage aus der Aufgabe. Die Generatoren folgen den Vorgaben:
- Sie verwenden `Gen.asciiPrintableStr`.
- Sie erzeugen gezielt fast gleiche Werte.
- Die Umgebungsschlüssel kommen aus einem kleinen Pool, damit Kollisionen vorkommen.
- Wo ein freier Wert nötig ist, dient `nonEmptyStringOf` dazu.

Alle vier Mutanten werden erkannt. Dass die Tests trotzdem die Punkte 1, 2, 5, 9 und 11 durchgelassen haben, liegt an den Lücken:

- **Wirkungsloser Filter** (`test/OpenRouterTests.test.scala:73`, code-review): `suchThat` prüft, ob der ganze Antworttext ein `RawAnswer` ist. Das ist für diese Generatoren nie der Fall, der Filter tut also nichts. Ergänzen: `choices` mit `null`, Zahlen und Zeichenketten, eine 200-Antwort mit `error` (Punkte 2 und 9).
- **Steuerzeichen:** Kein Generator erzeugt sie. Eigenschaft: Befehle mit C0-Zeichen oder ESC ergeben `Left` (Punkt 1).
- **PATH-Einträge:** Der Generator kennt keine Anführungszeichen, keine Schrägstriche und keine leeren Einträge. Ergänzen und prüfen, dass `run` nie wirft und WSL nie gewählt wird (Punkte 2 und 5).
- **Optionen als Wert:** `--model` und `--shell` gefolgt von einer Option ergeben einen Bedienfehler (Punkt 11).
- **`Main` ist ungetestet:** Exit-Codes 0, 1 und 2, die Reihenfolge (Schlüssel vor interaktiver Frage) und dass nach „nein“ nichts läuft. Vorschlag: `run` mit übergebenen Funktionen für Eingabe, Ausgabe, Senden und Ausführen.
- **`Executor.run` ist ungetestet:** ein Integrationstest mit `exit 3`, der `Right(3)` erwartet, und einer mit mehrzeiligem CRLF-Befehl (Punkt 7). Beide mit `assume`, nur wenn bash vorhanden ist.
- **Mehrzeilige Befehle:** Sie kommen in `Gens.answer` nicht vor, und die Eigenschaft „erste Zeile = Befehl“ würde an ihnen scheitern. Erst entscheiden, wie sie aussehen sollen.
- **Startskripte:** Sie sind ungetestet, obwohl die Punkte 3, 6 und 8 dort stecken. Mindestens eine Prüfliste für Handtests in die README: `%`, `^`, Umlaute ein und aus, Umleitung, Ausführen mit POSIX-Pfaden.

## Offene Fragen

- **PowerShell-Version:** Soll unter Windows Windows PowerShell 5.1 die Vorgabe sein, weil sie immer vorhanden ist, oder PowerShell 7, wenn `pwsh` gefunden wird? Das entscheidet über die Lösung von Punkt 4.
- **Steuerzeichen:** Soll `how` Befehle mit Steuerzeichen ablehnen (sicherer) oder sie sichtbar machen, etwa als `\r` (informativer)?
- **Exit-Code bei Rückfrage:** Er ist mit 2 derselbe wie bei Bedienfehlern. Soll ein Skript beides unterscheiden können, etwa mit 3 für die Rückfrage?
- **`#< CLIXML`:** Ist beim Ausführen in PowerShell die Fehlerausgabe umgeleitet, erscheinen XML-Fragmente. Im Terminal tritt das nicht auf. Soll das abgefangen werden?

## Priorität

1. **Punkt 1** (Steuerzeichen): Nur hier kann `how` etwas anderes ausführen, als der Nutzer bestätigt hat.
2. **Punkt 2** (Abstürze): Sie verletzen die Grundregel des Projekts. Der Umbau von `Executor.run` behebt dabei auch die unnötige Arbeit bei PowerShell.
3. **Punkte 3, 6 und 8** (Startskripte): Sie werden zusammen behoben, mit einer PowerShell-Funktion, `MSYS_NO_PATHCONV` nur für Java und dem Zeichensatz der Konsole.
4. **Punkte 4 und 5** (richtige Shell): Punkt 4 erst nach Klärung der offenen Frage.
5. **Punkte 7, 9 und 11** und die Testlücken: Die neuen Eigenschaften zuerst schreiben, sie müssen an den heutigen Fehlern scheitern.
6. **Design-Punkte und Punkt 10:** Aufräumen.

## Stand der Behebung (18.11.2026)

Entscheidungen des Nutzers:
- PowerShell 7, wenn vorhanden.
- Befehle mit Steuerzeichen ablehnen.
- Überall der Name `how`.
- Für PowerShell bleibt `how.cmd`, die Ausführungsrichtlinie wird nicht geändert.

Vorgehen: zuerst die Tests. Sie sind von 45 auf 78 gewachsen. Danach acht Mutanten mit den alten Fehlern, alle erkannt.

| Punkt | Stand |
|---|---|
| 1 Steuerzeichen | Behoben. `Answer.from` lehnt Steuerzeichen in allen Texten ab, auch am Rand vor `trim`, und Formatzeichen im Befehl. |
| 2 Exceptions | Behoben. JSON-Zugriffe und `Path.of` laufen in `Try`, ebenso das Senden samt `close`. `HowError.describe` nimmt die innerste Meldung (neu gefunden: sttp verpackt „connection refused“ in eine allgemeine Meldung). |
| 3 `%` in `how.cmd` | Bleibt als dokumentierte Grenze (README, Hilfe, Kommentar in `how.cmd`). Ausweg: `how` ohne Argumente, im echten Lauf mit `%PATH%` geprüft. |
| 4 PowerShell 5.1/7 | Behoben. `Shell` kennt `WindowsPowerShell` und `PowerShell7`. `powershell` heißt 7, wenn vorhanden. Der Prompt verbietet `&&` für 5.1; echter Lauf: `npm run build; if ($?) { npm test }`. Mangels `pwsh` auf diesem Rechner nur mit Tests geprüft. |
| 5 Wahl der bash | Behoben. Reihenfolge `HOW_BASH` (vom Startskript), `SHELL`, PATH. Pfade werden bereinigt (Anführungszeichen, `/`), WSL-Starter übergangen. Ohne Treffer gibt es eine klare Meldung. |
| 6 `MSYS_NO_PATHCONV` | Behoben. Wird nur für Java gesetzt und beim Ausführen entfernt, ebenso `HOW_BASH`. |
| 7 CRLF | Behoben. `Answer.from` macht aus `\r\n` ein `\n`. |
| 8 Umlaute | Ausgabe behoben: `how.cmd` gibt die Codepage der Konsole an Java weiter, im echten Lauf mit umgeleiteter Ausgabe geprüft. Eingabe: Sie läuft jetzt über `System.console().reader()`. In einer echten Konsole noch von Hand zu prüfen. |
| 9 Fehler bei HTTP 200 | Behoben. Neuer Fall `HowError.Upstream` mit der Meldung des Anbieters. |
| 10 Farben | Behoben. Der Befehl wird nie gefärbt. |
| 11 Option als Wert | Behoben, für `--model` und `--shell`. |
| Design | Alle Punkte behoben: `Option(System.console())`; BOM als `0xfeff.toChar` in `Input.clean`, angewandt in `App` auf jede Eingabe; für PowerShell weder bash-Suche noch Skriptdatei; eine Hilfsfunktion für Exceptions; scalafmt mit `.scalafmt.conf`. |
| Tests | Alle Lücken geschlossen: `App` über die Schnittstelle `Io`, `Executor.run` mit echten Prozessen, Steuer- und Formatzeichen, ungewöhnliche PATH-Einträge, JSON-Werte jeder Art in `choices`, mehrzeilige Befehle. Für die Startskripte gibt es eine Prüfliste für Handtests in der README. |

Weiter offen:
- Exit-Code 2 bei Rückfrage: unverändert.
- `#< CLIXML`: tritt bei umgeleiteter Fehlerausgabe weiterhin auf.
