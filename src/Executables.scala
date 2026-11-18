package how

/** Sucht Programme im PATH, ohne selbst aufs Dateisystem zuzugreifen: Ob eine Datei existiert, sagt `exists`. */
object Executables:

  /** Die Verzeichnisse aus PATH, bereinigt. Unter Windows heißt die Variable oft `Path`. */
  def pathDirs(env: Map[String, String], osName: String): List[String] =
    val separator = if Shell.isWindows(osName) then ';' else ':'
    env
      .collectFirst { case (key, value) if key.equalsIgnoreCase("PATH") => value }
      .toList
      .flatMap(_.split(separator))
      .map(cleanDir(osName))
      .filter(_.nonEmpty)

  /** Mögliche Pfade des Programms, in der Reihenfolge des PATH. */
  def candidates(name: String, env: Map[String, String], osName: String): List[String] =
    if Shell.isWindows(osName) then pathDirs(env, osName).map(dir => s"$dir\\$name.exe")
    else pathDirs(env, osName).map(dir => s"$dir/$name")

  def find(name: String, env: Map[String, String], osName: String, exists: String => Boolean): Option[String] =
    candidates(name, env, osName).find(exists)

  /** Bereinigt einen Pfad zu einer Datei. Unter Windows sind Anführungszeichen in PATH erlaubt, in Dateinamen nicht,
    * und `/` gilt wie `\`; beides wird vereinheitlicht, damit Vergleiche (z. B. auf System32) zuverlässig sind.
    */
  def cleanFile(osName: String)(path: String): String =
    if Shell.isWindows(osName) then path.trim.filterNot(_ == '"').replace('/', '\\') else path.trim

  private def cleanDir(osName: String)(dir: String): String =
    val clean = cleanFile(osName)(dir)
    if Shell.isWindows(osName) then clean.stripSuffix("\\") else clean.stripSuffix("/")
