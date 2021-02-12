import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val akkaVersion = "2.6.12"
  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                %% "bootstrap-backend-play-27"        % "3.4.0",
    "com.github.java-json-tools" % "json-schema-validator"     % "2.2.14",
    "org.reactivemongo"          %% "play2-reactivemongo"      % "0.18.8-play27",
    "com.typesafe.play"       %% "play-json-joda"             % "2.7.4",
    "org.reactivemongo"          %% "reactivemongo-akkastream" % "0.20.3",
    "com.typesafe.akka"          %% "akka-slf4j"               % akkaVersion,
    "com.typesafe.akka"          %% "akka-actor"               % akkaVersion,
    "com.typesafe.akka"          %% "akka-stream"              % akkaVersion
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"                % "3.0.9",
    "com.typesafe.play"        %% "play-test"                % current,
    "org.pegdown"              %  "pegdown"                  % "1.6.0",
    "org.mockito"              %  "mockito-all"              % "2.0.2-beta",
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.27.2",
    "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play"       % "4.0.0",
    "org.scalacheck"           %% "scalacheck"               % "1.14.3",
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.22.0-play-27"
  ).map(_ % "test,it")
}
