import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val pluginName = "sbt-artifactory"

// We are shading the project's dependencies.
// The main motivation for this is a conflict between dependencies of global and local plugins.
// On sbt 1.3.x their dependencies are merged before evicting, cause some runtime errors. This
// seems to be resolved on sbt 1.4.x.
lazy val project = Project(pluginName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory, ShadingPlugin)
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
    addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6"
      // exclude "provided" otherwise they will be included in shaded jar
      // scala-xml contains "scala-xml.properties" which can't be excluded with validNamespaces
      // (TODO should they be added to libaryDependencies?)
      exclude("org.scala-lang.modules", "scala-xml_2.12")
      exclude("org.scala-lang", "*")
      // TODO infact we could exclude all except dispatch? - this is the one which is causing problems
    ),
    // *********************************
    libraryDependencies ++= Seq(
      "com.typesafe.play"     %% "play-json"                  % "2.6.14",
      "org.joda"              % "joda-convert"                % "2.2.1",
      "org.scalatest"         %% "scalatest"                  % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito"      % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  % "flexmark-all"                % "0.35.10"  % Test // replaces pegdown for newer scalatest
    ),

    shadedModules += "org.foundweekends" % "sbt-bintray",
    shadingRules += ShadingRule.moveUnder("dispatch", "uk.gov.hmrc.sbt-artifactory.shaded"),
    shadingRules += ShadingRule.moveUnder("com",      "uk.gov.hmrc.sbt-artifactory.shaded"),
    shadingRules += ShadingRule.moveUnder("bintry",   "uk.gov.hmrc.sbt-artifactory.shaded"),
    shadingRules += ShadingRule.moveUnder("bintray",  "uk.gov.hmrc.sbt-artifactory.shaded"),
    validNamespaces += "uk",
    validNamespaces += "sbt", // sbt/sbt.autoplugins needs to be in route
    validNamespaces += "org", // org.scalajs.sbtplugin.ScalaJSPlugin$AutoImport$ needs to be untouched

    shadingVerbose := true,
  )
