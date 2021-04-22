import sbt.Defaults.sbtPluginExtra
import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val name = "sbt-artifactory"

val dispatch = (sbtVersion in pluginCrossBuild) {
  case v if v startsWith "0.13" => "net.databinder.dispatch" %% "dispatch-core" % "0.11.4"
  case v if v startsWith "1.3"  => "net.databinder.dispatch" %% "dispatch-core" % "0.13.4"
}

lazy val project = Project(name, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    majorVersion     := 1,
    isPublicArtefact := true
  )
  .settings(
    sbtPlugin := true,
    scalaVersion := "2.12.12",
    crossSbtVersions := Vector("0.13.18", "1.3.13"),
    addSbtPlugin("org.scala-js" % "sbt-scalajs"      % "0.6.31"),
    addSbtPlugin("uk.gov.hmrc"  % "sbt-setting-keys" % "0.2.0" ),
    libraryDependencies ++= Seq(
      "com.typesafe.play"     %% "play-json"             % "2.6.14",
      "org.joda"              %  "joda-convert"          % "2.2.1",
      "org.scalatest"         %% "scalatest"             % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito" % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  %  "flexmark-all"          % "0.35.10"  % Test // required by scalatest
    ) :+ dispatch.value,
    resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
  )
