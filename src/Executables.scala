package how

/** Finds programs on the PATH without touching the file system itself: `exists` says whether a file exists. */
object Executables:

  /** The directories from PATH, cleaned. On Windows the variable is often called `Path`. */
  def pathDirs(env: Map[String, String], osName: String): List[String] =
    val separator = if Shell.isWindows(osName) then ';' else ':'
    env
      .collectFirst { case (key, value) if key.equalsIgnoreCase("PATH") => value }
      .toList
      .flatMap(_.split(separator))
      .map(cleanDir(osName))
      .filter(_.nonEmpty)

  /** Possible paths of the program, in PATH order. */
  def candidates(name: String, env: Map[String, String], osName: String): List[String] =
    if Shell.isWindows(osName) then pathDirs(env, osName).map(dir => s"$dir\\$name.exe")
    else pathDirs(env, osName).map(dir => s"$dir/$name")

  def find(name: String, env: Map[String, String], osName: String, exists: String => Boolean): Option[String] =
    candidates(name, env, osName).find(exists)

  /** Cleans a path to a file. On Windows, quotes are allowed in PATH but not in file names, and `/` works like `\`;
    * both are unified so that comparisons (e.g. with System32) are reliable.
    */
  def cleanFile(osName: String)(path: String): String =
    if Shell.isWindows(osName) then path.trim.filterNot(_ == '"').replace('/', '\\') else path.trim

  private def cleanDir(osName: String)(dir: String): String =
    val clean = cleanFile(osName)(dir)
    if Shell.isWindows(osName) then clean.stripSuffix("\\") else clean.stripSuffix("/")
