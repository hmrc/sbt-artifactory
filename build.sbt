import sbt.Defaults.sbtPluginExtra
import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val name = "sbt-artifactory"

val sbtV = sbtBinaryVersion in pluginCrossBuild
val scalaV = scalaBinaryVersion in update

val dispatch = (sbtVersion in pluginCrossBuild) {
  case v if v startsWith "0.13" => "net.databinder.dispatch" %% "dispatch-core" % "0.11.4"
  case v if v startsWith "1.3" => "net.databinder.dispatch" %% "dispatch-core" % "0.13.4"
}

lazy val project = Project(name, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    majorVersion := 1,
    makePublicallyAvailableOnBintray := true
  )
  .settings(
    sbtPlugin := true,
    scalaVersion := "2.12.12",
    crossSbtVersions := Vector("0.13.18", "1.3.13"),
    libraryDependencies ++= Seq(
      sbtPluginExtra("org.scala-js" % "sbt-scalajs" % "0.6.31", sbtV.value, scalaV.value),
      "com.typesafe.play"     %% "play-json"                  % "2.6.14",
      "org.joda"              % "joda-convert"                % "2.2.1",
      "org.scalatest"         %% "scalatest"                  % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito"      % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  % "flexmark-all"                % "0.35.10"  % Test, // replaces pegdown for newer scalatest
    ) :+ dispatch.value
  )
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "uk.gov.hmrc.sbtartifactory"
  )
