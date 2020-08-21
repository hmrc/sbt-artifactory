import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val pluginName = "sbt-artifactory"

lazy val project = Project(pluginName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    majorVersion := 1,
    makePublicallyAvailableOnBintray := true
  )
  .settings(
    sbtPlugin := true,
    crossSbtVersions := Vector("0.13.18", "1.3.13"),
    // *********************************
    // Note: The sbt-scalajs plugin is brought in as a *project* dependency. It is *not* pulling in the plugin for
    //       the build as you might expect. Code in the plugin is used in the SbtArtifactory object.
    scalaVersion := "2.12.12",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.31"),
    addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6"),
    // *********************************
    libraryDependencies ++= Seq(
      "com.typesafe.play"     %% "play-json"                  % "2.6.14",
      "org.joda"              % "joda-convert"                % "2.2.1",
      "org.scalatest"         %% "scalatest"                  % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito"      % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  % "flexmark-all"                % "0.35.10"  % Test // replaces pegdown for newer scalatest
    ),
    // There is no release of dispatch cross-compiled for both scala 2.10 and scala 2.12 (required by sbt 0.13/1.x)
    // As a result, use a version dependent on the sbtVersion
    libraryDependencies ++= (sbtVersion in pluginCrossBuild) { version =>
      val dispatchVersion = version match {
        case v if v startsWith "0.13" => "0.11.4"
        case v if v startsWith "1.3" => "0.12.0"
      }
      Seq("net.databinder.dispatch" %% "dispatch-core" % dispatchVersion)
    }.value
  )
