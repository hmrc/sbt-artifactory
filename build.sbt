import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val pluginName = "sbt-artifactory"

// We are shading the project's dependencies.
// The main motivation for this is a conflict between dependencies of global and local plugins,
// principally dispatch-core as pulled in by sbt-bintray.
// On sbt 1.3.x their dependencies are merged before evicting, cause some runtime errors. This
// seems to be resolved on sbt 1.4.x.
val shadedPackages = Seq(
  // bintray and transitives
  "bintry",
  "bintray",
  "com.ning",
  "com.thoughtworks",
  "dispatch",
  "org.jboss",
  "org.json4s",
  "org.slf4j",
  // other dependencies
  "com",
  "macrocompat",
  "org.joda",
  "play"
)

lazy val commonSettings = Seq(
  organization     := "uk.gov.hmrc",
  majorVersion     := 1,
  scalaVersion     := "2.12.12",
  sbtPlugin        := true,
  crossSbtVersions := Vector("0.13.18", "1.3.13"),
  makePublicallyAvailableOnBintray := true
)

lazy val root = (project in file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    commonSettings,
    publish / skip := true
  )
  .aggregate(adjusted)

lazy val shaded = Project("shaded", file("shaded"))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    commonSettings,
    publish / skip := true,
    // these plugins are library dependencies
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.31" % Provided), // not included in fatjar, add as a downloadable dependency - it pulls in 20 MB (incl. google libs which have duplications)
    addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6"),
    libraryDependencies ++= Seq(
      "com.typesafe.play"     %% "play-json"                  % "2.6.14",
      "org.joda"              % "joda-convert"                % "2.2.1",
      "org.scalatest"         %% "scalatest"                  % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito"      % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  % "flexmark-all"                % "0.35.10"  % Test // replaces pegdown for newer scalatest
    ),

    // sbt_bintray pulls in dispatch-core 0.11.2, which strips out trailing `/`, breaking delete calls to Artifactory
    dependencyOverrides ++= (sbtVersion in pluginCrossBuild) {
      case v if v startsWith "0.13" => Seq("net.databinder.dispatch" %% "dispatch-core" % "0.11.4")
      case v if v startsWith "1.3"  => Seq.empty[ModuleID]
    }.value,

    assembly / assemblyMergeStrategy := {
      case PathList("sbt", "sbt.autoplugins") =>
        // Not using MergeStrategy.filterDistinctLines since content has not been updated to point to shaded version. We don't want to activate them anyway.
        MergeStrategy.first // this is ours
      // default
      case x => (assembly / assemblyMergeStrategy).value(x)
    },
    assembly / assemblyOption := (assembly / assemblyOption).value.copy(includeScala = false),
    assembly / assemblyShadeRules := shadedPackages.map(pkg =>
      ShadeRule.rename(s"$pkg.**" -> s"uk.gov.hmrc.sbt-artifactory.shaded.$pkg.@1").inAll
    ),
    Compile / assembly / artifact :=
      (Compile / assembly / artifact).value
        .withClassifier(Some("assembly")),
    addArtifact(Compile / assembly / artifact, assembly)  )

lazy val adjusted = Project(pluginName, file("transient"))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    commonSettings,
    // publish the shaded bin
    Compile / packageBin := (shaded / Compile / assembly  ).value,
    Compile / packageSrc := (shaded / Compile / packageSrc).value,
    Compile / packageDoc := (shaded / Compile / packageDoc).value,

    // add dependencies which were not shaded
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.31")
)