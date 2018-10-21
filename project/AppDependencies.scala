import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-play-26"        % "0.23.0",
    "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.1.0-play-26"
  )

  val test = Seq(
    "org.scalatest"           %% "scalatest"                % "3.0.4"                 % "test",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.0"                 % "test"
  )
}
