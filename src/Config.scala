package how

final case class Config(apiKey: String, model: String)

object Config:

  val DefaultModel = "anthropic/claude-haiku-5.5"

  /** Model: `--model` before `HOW_MODEL` before the default. */
  def fromEnv(env: Map[String, String], modelOption: Option[String]): Either[HowError, Config] =
    val model = modelOption
      .orElse(env.get("HOW_MODEL").map(_.trim).filter(_.nonEmpty))
      .getOrElse(DefaultModel)
    env.get("OPENROUTER_API_KEY").map(_.trim).filter(_.nonEmpty) match
      case Some(key) => Right(Config(key, model))
      case None      => Left(HowError.MissingApiKey)
