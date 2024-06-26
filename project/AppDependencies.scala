import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "8.6.0"
  private lazy val hmrcMongoVersion     = "2.1.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "com.github.java-json-tools"    % "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.17.1"
  )

  private val test: Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"              % "1.18.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID]      = compile ++ test

}
