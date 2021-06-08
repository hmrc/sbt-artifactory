import sbt.Keys._
import sbt._

lazy val project = Project("sbt-artifactory", file("."))
  .settings(
    majorVersion     := 2,
    isPublicArtefact := true
  )
  .settings(
    sbtPlugin := true,
    scalaVersion := "2.12.12",
    crossSbtVersions := Vector("0.13.18", "1.3.13"),
    addSbtPlugin("uk.gov.hmrc"  % "sbt-setting-keys" % "0.3.0" ),
    libraryDependencies ++= Seq(
      "org.scalatest"         %% "scalatest"             % "3.2.1"    % Test,
      "org.scalatestplus"     %% "scalatestplus-mockito" % "1.0.0-M2" % Test,
      "com.vladsch.flexmark"  %  "flexmark-all"          % "0.35.10"  % Test // required by scalatest
    ),
    resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
  )
