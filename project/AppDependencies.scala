import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                %% "bootstrap-play-26"        % "0.46.0",
    "com.github.java-json-tools" % "json-schema-validator"     % "2.2.10",
    "org.reactivemongo"          %% "play2-reactivemongo"      % "0.18.5-play26",
    "org.reactivemongo"          %% "reactivemongo-akkastream" % "0.18.5",
    "com.typesafe.akka"          %% "akka-slf4j"               % "2.5.25"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"                % "3.0.8",
    "com.typesafe.play"        %% "play-test"                % current,
    "org.pegdown"              %  "pegdown"                  % "1.6.0",
    "org.mockito"              %  "mockito-all"              % "2.0.2-beta",
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.24.1",
    "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play"       % "3.1.2",
    "org.scalacheck"           %% "scalacheck"               % "1.14.0"
  ).map(_ % "test,it")
}
