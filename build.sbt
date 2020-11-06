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
  "bintry",
  "bintray",
  "com.ning",
  "com.thoughtworks",
  "dispatch",
  "org.jboss",
  "org.json4s",
  "org.slf4j"
)

lazy val project = Project(pluginName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning/*, SbtArtifactory*/)
  .settings(
    majorVersion := 1/*,
    makePublicallyAvailableOnBintray := true*/
  )
  .settings(
    sbtPlugin := true,

    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))),

    crossSbtVersions := Vector("0.13.18", "1.3.13"),
    // *********************************
    // Note: The sbt-scalajs plugin is brought in as a *project* dependency. It is *not* pulling in the plugin for
    //       the build as you might expect. Code in the plugin is used in the SbtArtifactory object.
    scalaVersion := "2.12.12",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.31"),
    addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6"
      // exclude "provided" otherwise they will be included in shaded jar
      // scala-xml contains "scala-xml.properties" which can't be excluded with validNamespaces
      exclude("org.scala-lang.modules", "scala-xml_2.12") // not a dependency for 2.10 (sbt 0.13)
      exclude("org.scala-lang", "*")
    ),
    // *********************************
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
      case v if v startsWith "1.3" => Seq.empty[ModuleID]
    }.value,

assemblyMergeStrategy in assembly := {
  case PathList("sbt", "sbt.autoplugins")         => MergeStrategy.first
  // google dependencies have duplications
  case PathList("javax", "annotation", xs @ _*)       => MergeStrategy.first
  case PathList("com", "google", "protobuf", xs @ _*) => MergeStrategy.first
  case PathList("google", "protobuf", xs @ _*)        => MergeStrategy.first
  case PathList("protobuf", xs @ _*)                  => MergeStrategy.first

  //javax/annotation/Nonnull$Checker.class
  /*case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard*/
  case x => (assemblyMergeStrategy in assembly).value(x)
},
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

     assemblyShadeRules in assembly := shadedPackages.map( pkg =>
      ShadeRule.rename(s"$pkg.**" -> s"uk.gov.hmrc.sbt-artifactory.shaded.$pkg.@1").inAll
    ),
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },
    addArtifact(artifact in (Compile, assembly), assembly),

    /*shadedModules   += "org.foundweekends" % "sbt-bintray",
    shadingRules    ++= shadedPackages.map(ShadingRule.moveUnder(_, "uk.gov.hmrc.sbt-artifactory.shaded")),
    validNamespaces += "uk", // doesn't support nested namespaces (e.g. "uk.gov.hmrc") since it matches all directories in the created jar (including parent directories)
    validNamespaces += "sbt" // sbt/sbt.autoplugins needs to be in root*/

    publishMavenStyle := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
  )
