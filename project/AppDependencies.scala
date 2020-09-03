import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                %% "bootstrap-play-26"        % "1.14.0",
    "com.github.java-json-tools" % "json-schema-validator"     % "2.2.14",
    "org.reactivemongo"          %% "play2-reactivemongo"      % "0.18.8-play26",
    "org.reactivemongo"          %% "reactivemongo-akkastream" % "0.18.8",
    "com.typesafe.akka"          %% "akka-slf4j"               % "2.5.31"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"                % "3.0.8",
    "com.typesafe.play"        %% "play-test"                % current,
    "org.pegdown"              %  "pegdown"                  % "1.6.0",
    "org.mockito"              %  "mockito-all"              % "2.0.2-beta",
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.27.1",
    "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play"       % "3.1.3",
    "org.scalacheck"           %% "scalacheck"               % "1.14.3"
  ).map(_ % "test,it")
}
