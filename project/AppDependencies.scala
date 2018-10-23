import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-play-26"        % "0.23.0",
    "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.1.0-play-26"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"                % "3.0.4",
    "com.typesafe.play"       %% "play-test"                % current,
    "org.pegdown"             %  "pegdown"                  % "1.6.0",
    "org.mockito"             %  "mockito-all"              % "1.10.19",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.0"
  ).map(_ % Test)
}
