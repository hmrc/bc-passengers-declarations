import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq("<empty>", "Reverse.*", "..*Routes.*")

  private val settings: Seq[Setting[?]] = Seq(
    coverageExcludedPackages := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 97,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

  def apply(): Seq[Setting[?]] = settings
}
