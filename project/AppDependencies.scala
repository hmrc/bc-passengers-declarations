import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val akkaVersion = "2.6.15"
  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28" % "5.12.0",
    "com.github.java-json-tools" % "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"          %% "hmrc-mongo-play-28"         % "0.68.0",
    "uk.gov.hmrc"                %% "play-json-union-formatter"  % "1.15.0-play-28",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "3.0.1",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion,
    "org.reactivemongo" %% "reactivemongo-akkastream" % "0.20.13",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.4",
    "com.typesafe.akka"          %% "akka-actor-typed"              % akkaVersion,
    "com.typesafe.akka"          %% "akka-serialization-jackson"              % akkaVersion
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"                % "3.2.9",
    "com.typesafe.play"        %% "play-test"                % current,
    "org.pegdown"              %  "pegdown"                  % "1.6.0",
    "org.mockito"              %  "mockito-all"              % "2.0.2-beta",
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.27.2",
    "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play" % "5.1.0",
    "org.scalacheck"           %% "scalacheck"               % "1.14.3",
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-28"    % "0.68.0",
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"   % "5.12.0",
    "org.scalatestplus"      %% "mockito-3-4"              % "3.2.9.0"
  ).map(_ % "test,it")
}

