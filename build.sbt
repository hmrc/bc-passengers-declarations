import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "bc-passengers-declarations"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(
    majorVersion                     := 0,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    RoutesKeys.routesImport          += "models.ChargeReference"
  )
  .settings(publishingSettings: _*)
  .settings(resolvers += Resolver.jcenterRepo)
