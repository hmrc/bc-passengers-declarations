import sbt._

object AppDependencies {

  val akkaVersion            = "2.6.20"
  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28"   % "7.12.0",
    "com.github.java-json-tools"    % "json-schema-validator"       % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"          % "0.74.0",
    "uk.gov.hmrc"                  %% "play-json-union-formatter"   % "1.18.0-play-28",
    "com.typesafe.play"            %% "play-json-joda"              % "2.9.3",
    "com.lightbend.akka"           %% "akka-stream-alpakka-mongodb" % "5.0.0",
    "com.typesafe.akka"            %% "akka-slf4j"                  % "2.7.0",
    "com.typesafe.akka"            %% "akka-actor"                  % akkaVersion,
    "com.typesafe.akka"            %% "akka-stream"                 % akkaVersion,
    "com.typesafe.akka"            %% "akka-protobuf-v3"            % akkaVersion,
    "org.reactivemongo"            %% "reactivemongo-akkastream"    % "1.0.10",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.14.1",
    "com.typesafe.akka"            %% "akka-actor-typed"            % "2.7.0",
    "com.typesafe.akka"            %% "akka-serialization-jackson"  % "2.7.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"               % "3.2.15",
    "com.typesafe.play"       %% "play-test"               % "2.8.19",
    "org.mockito"              % "mockito-all"             % "2.0.2-beta",
    "com.github.tomakehurst"   % "wiremock-standalone"     % "2.27.2",
    "com.github.netcrusherorg" % "netcrusher-core"         % "0.10",
    "org.scalatestplus.play"  %% "scalatestplus-play"      % "5.1.0",
    "org.scalacheck"          %% "scalacheck"              % "1.17.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28" % "0.74.0",
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"  % "7.12.0",
    "org.scalatestplus"       %% "mockito-3-4"             % "3.2.10.0",
    "com.vladsch.flexmark"     % "flexmark-all"            % "0.62.2"
  ).map(_ % "test,it")
}
