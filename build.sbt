import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "bc-passengers-declarations"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(inConfig(Test)(testSettings): _*)
  .settings(scalaVersion := "2.12.12")
  .settings(
    PlayKeys.playDefaultPort                      := 9073,
    majorVersion                                  := 0,
    libraryDependencies                           ++= AppDependencies.compile ++ AppDependencies.test,
    RoutesKeys.routesImport                       += "models.ChargeReference"
  )
  .settings(publishingSettings: _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;..*Routes.*;",
    ScoverageKeys.coverageMinimum := 80
  )

lazy val testSettings = Seq(
  unmanagedSourceDirectories   += baseDirectory.value / "test-utils",
  unmanagedResourceDirectories += baseDirectory.value / "test-utils" / "resources",
  fork                         := true,
  javaOptions                  ++= Seq(
    "-Dconfig.resource=test.application.conf"
  )
)

lazy val itSettings = Defaults.itSettings ++ Seq(
  unmanagedSourceDirectories   := Seq(
    baseDirectory.value / "it",
    baseDirectory.value / "test-utils"
  ),
  unmanagedResourceDirectories := Seq(
    baseDirectory.value / "it" / "resources",
    baseDirectory.value / "test-utils" / "resources"
  ),
  parallelExecution            := false,
  fork                         := true,
  javaOptions                  ++= Seq(
    "-Dconfig.resource=it.application.conf",
    "-Dlogger.resource=it.logback.xml"
  )
)
