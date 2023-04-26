import uk.gov.hmrc.DefaultBuildSettings._

val appName = "bc-passengers-declarations"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(inConfig(Test)(testSettings): _*)
  .settings(scalaVersion := "2.13.10")
  .settings(
    PlayKeys.playDefaultPort := 9073,
    majorVersion := 0,
    // To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:postfixOps"
    ),
    routesImport += "models.ChargeReference"
  )
  .settings(
    coverageExcludedPackages := "<empty>;Reverse.*;..*Routes.*;",
    coverageMinimumStmtTotal := 95,
    coverageFailOnMinimum := true
  )

lazy val testSettings = Seq(
  unmanagedSourceDirectories += baseDirectory.value / "test-utils",
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources",
  fork := true,
  javaOptions ++= Seq(
    "-Dconfig.resource=test.application.conf"
  )
)

lazy val itSettings = Defaults.itSettings ++ Seq(
  unmanagedSourceDirectories := Seq(
    baseDirectory.value / "it",
    baseDirectory.value / "test-utils"
  ),
  unmanagedResourceDirectories := Seq(
    baseDirectory.value / "it" / "resources",
    baseDirectory.value / "test-utils" / "resources"
  ),
  parallelExecution := false,
  fork := true,
  javaOptions ++= Seq(
    "-Dconfig.resource=it.application.conf",
    "-Dlogger.resource=it.logback.xml"
  )
)

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle")
