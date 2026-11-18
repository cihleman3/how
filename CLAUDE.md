# how

Kommandozeilenwerkzeug: `how <Frage in freien Worten>` liefert einen kopierfertigen Shell-Befehl und eine kurze Erklärung. Das Modell wird über OpenRouter angesprochen.

Die globalen Scala-Richtlinien gelten. Zusätzlich gilt für dieses Projekt:

## Aufbau

- Reiner Kern im Paket `how`: `Cli`, `Shell`, `Prompt`, `Answer`, `Render`, `Config`, `Input`, `Executables`, `App` und die reinen Teile von `OpenRouter` und `Executor`. Kein Ein- und Ausgabezugriff, keine Umgebungsvariablen, kein Netz, kein Dateisystem. Alles, was von außen kommt, wird als Parameter übergeben (z. B. `env: Map[String, String]`, `osName: String`, `exists: String => Boolean`).
- `App.run` ist der Ablauf eines Aufrufs. Alle Effekte bekommt es über die Schnittstelle `Io`; `Main.RealIo` liefert die echten, `AppTests` eigene.
- Effekte nur am Rand:
  - `Main.RealIo` für Konsole und Eingabe,
  - `OpenRouter.ask` für HTTP,
  - `Executor.run` und `Executor.fileExists` für Prozesse und Dateisystem.
  Jede Effektfunktion ist dünn und ruft reine Funktionen auf, die getestet sind. Was gestartet wird, plant `Executor.plan` als Wert.
- Fehler als Wert: `enum HowError`. Exceptions von sttp, ujson und Prozessen werden am Rand mit `Try` gefangen und mit `HowError.describe` in einen `HowError` übersetzt. Sie dürfen nicht nach außen dringen; auch jeder Zugriff auf JSON und jedes `Path.of` läuft in einem `Try`.
- Antworten des Modells sind nicht vertrauenswürdig: `Answer.from` lehnt Steuer- und Formatzeichen ab, damit angezeigt wird, was ausgeführt wird.
- Formatierung mit scalafmt (`.scalafmt.conf`).
- Keine Escape-Sequenzen wie `﻿` oder `\u001b` im Quelltext, die ein Werkzeug als echtes Zeichen schreiben könnte; Sonderzeichen als Zahl (`0xfeff.toChar`, `27.toChar`).
- Die Ausgabe ist das Produkt: Der Befehl steht allein auf seiner Zeile, ohne Präfix, damit er sich kopieren lässt.
- Texte der Oberfläche auf Englisch. Die Erklärung des Modells kommt in der Sprache der Frage.

## Sicherheit

- Der API-Schlüssel kommt nur aus der Umgebungsvariable `OPENROUTER_API_KEY`. Nie in Code, Tests, Logs oder Fehlermeldungen.
- Ein Befehl wird nur nach ausdrücklicher Bestätigung ausgeführt (Standard: nein).

## Tests

- MUnit mit ScalaCheck-Eigenschaften, dazu Beispieltests mit der Frage aus der Aufgabe.
- Tests rufen nie die echte API auf: Anfragen werden als Wert geprüft, Antworten mit `SyncBackendStub` simuliert.
- Testen mit `scala-cli test . --server=false`.

## Werkzeuge

- Metals (MCP) verwenden, wenn verfügbar: für Navigation, Verweise und Diagnosen.
