import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "7.19.0"
  private lazy val hmrcMongoVersion     = "1.3.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "com.github.java-json-tools"    % "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "com.typesafe.play"            %% "play-json-joda"            % "2.9.4",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.15.2"
  )

  private val test: Seq[ModuleID] = Seq(
    "org.scalatest"         %% "scalatest"               % "3.2.16",
    "com.typesafe.play"     %% "play-test"               % "2.8.20",
    "org.mockito"           %% "mockito-scala-scalatest" % "1.17.14",
    "com.github.tomakehurst" % "wiremock-standalone"     % "2.27.2",
    "org.scalacheck"        %% "scalacheck"              % "1.17.0",
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "uk.gov.hmrc"           %% "bootstrap-test-play-28"  % bootstrapPlayVersion,
    "com.vladsch.flexmark"   % "flexmark-all"            % "0.64.8"
  ).map(_ % "test,it")

  def apply(): Seq[ModuleID]      = compile ++ test
}
