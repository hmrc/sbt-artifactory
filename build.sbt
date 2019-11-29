import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val pluginName = "sbt-artifactory"

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.31")

lazy val project = Project(pluginName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    majorVersion := 0,
    makePublicallyAvailableOnBintray := true
  )
  .settings(
    sbtPlugin := true,
    crossSbtVersions := Vector("0.13.18", "1.3.4"),
    scalaVersion := "2.11.12",
    resolvers += Resolver.url("sbt-plugin-releases", url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
      Resolver.ivyStylePatterns),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.6.13",
      "org.joda" % "joda-convert" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "org.mockito" % "mockito-all" % "1.10.19" % Test,
      "org.pegdown" % "pegdown" % "1.6.0" % Test
    ),
    libraryDependencies ++= (sbtVersion in pluginCrossBuild) { version =>
      val dispatchVersion = version match {
        case v if v startsWith "0.13" => "0.11.4"
        case v if v startsWith "1.3" => "0.13.4"
      }
      Seq("net.databinder.dispatch" %% "dispatch-core" % dispatchVersion)
    }.value
  )
