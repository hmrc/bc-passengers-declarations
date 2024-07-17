import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / majorVersion := 0

lazy val microservice = Project("bc-passengers-declarations", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings.settings)
  .settings(
    PlayKeys.playDefaultPort := 9073,
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:postfixOps"
    ),
    routesImport += "models.ChargeReference",
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / javaOptions ++= Seq(
      "-Dconfig.resource=test.application.conf"
    )
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "-Dconfig.resource=it.application.conf",
      "-Dlogger.resource=it.logback.xml"
    )
  )

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt it/Test/scalafmt")
