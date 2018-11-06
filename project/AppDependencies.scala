import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                %% "bootstrap-play-26"        % "0.26.0",
    "com.github.java-json-tools" % "json-schema-validator"     % "2.2.8",
    "org.reactivemongo"          %% "play2-reactivemongo"      % "0.16.0-play26",
    "org.reactivemongo"          %% "reactivemongo-akkastream" % "0.16.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"                % "3.0.4",
    "com.typesafe.play"        %% "play-test"                % current,
    "org.pegdown"              %  "pegdown"                  % "1.6.0",
    "org.mockito"              %  "mockito-all"              % "1.10.19",
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.17.0",
    "com.github.netcrusherorg" % "netcrusher-core"           % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play"       % "2.0.0",
    "org.scalacheck"           %% "scalacheck"               % "1.13.4"
  ).map(_ % "test,it")
}
