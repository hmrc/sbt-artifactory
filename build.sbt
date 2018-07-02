import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val pluginName = "sbt-artifactory"

lazy val project = Project(pluginName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    sbtPlugin := true,
    targetJvm := "jvm-1.7",
    scalaVersion := "2.10.7",
    resolvers += Resolver.url("sbt-plugin-releases", url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
      Resolver.ivyStylePatterns),
    libraryDependencies ++= Seq(
      "net.databinder.dispatch" %% "dispatch-core" % "0.11.4",
      "org.scalatest"           %% "scalatest"     % "3.0.4"      % "test",
      "org.mockito"             %  "mockito-all"   % "1.10.19"    % "test",
      "org.pegdown"             %  "pegdown"       % "1.6.0"      % "test"
    )
  )
