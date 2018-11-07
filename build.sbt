import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "bc-passengers-declarations"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(
    PlayKeys.playDefaultPort                      := 9073,
    majorVersion                                  := 0,
    libraryDependencies                           ++= AppDependencies.compile ++ AppDependencies.test,
    RoutesKeys.routesImport                       += "models.ChargeReference",
    unmanagedSourceDirectories in Test            += baseDirectory.value / "test-utils",
    unmanagedResourceDirectories in Test          += baseDirectory.value / "test-utils" / "resources"
  )
  .settings(publishingSettings: _*)
  .settings(resolvers += Resolver.jcenterRepo)

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
