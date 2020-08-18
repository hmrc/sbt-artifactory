/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc

import org.scalajs.sbtplugin.ScalaJSCrossVersion
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.isScalaJSProject
import sbt.Keys._
import sbt.Resolver.ivyStylePatterns
import sbt._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import DispatchCrossSupport.Http

object SbtArtifactory extends sbt.AutoPlugin {

  private val distributionTimeout = 1 minute

  private val artifactoryUriEnvKey            = "ARTIFACTORY_URI"
  private val artifactoryUsernameEnvKey       = "ARTIFACTORY_USERNAME"
  private val artifactoryPasswordEnvKey       = "ARTIFACTORY_PASSWORD"
  private lazy val maybeArtifactoryUri        = sys.env.get(artifactoryUriEnvKey)
  private lazy val maybeArtifactoryUsername   = sys.env.get(artifactoryUsernameEnvKey)
  private lazy val maybeArtifactoryPassword   = sys.env.get(artifactoryPasswordEnvKey)

  val artifactoryLabsPattern = ".*lab([0-9]{2}).*".r

  private lazy val maybeArtifactoryCredentials = for {
    uri      <- maybeArtifactoryUri
    username <- maybeArtifactoryUsername
    password <- maybeArtifactoryPassword
  } yield directCredentials(uri, username, password)

  private val bintrayUsernameEnvKey = "BINTRAY_USERNAME"
  private val bintrayPasswordEnvKey = "BINTRAY_PASSWORD"
  private lazy val maybeBintrayUsername = sys.env.get(bintrayUsernameEnvKey)
  private lazy val maybeBintrayPassword = sys.env.get(bintrayPasswordEnvKey)

  object autoImport {
    val unpublishFromArtifactory = taskKey[Unit]("Unpublish from Artifactory.")
    val unpublishFromArtifactoryBintrayDistribution = taskKey[Unit]("Unpublish from Artifactory bintray-distribution.")
    val unpublishFromBintray = taskKey[Unit]("Unpublish from Bintray.")
    val unpublish = taskKey[Unit]("Unpublish from Artifactory.")
    val distributeToBintray =
      taskKey[Unit]("Distributes artifacts from an Artifactory Distribution Repository to Bintray")
    val publishAndDistribute =
      taskKey[Unit]("Publish to Artifactory and distribute to Bintray if 'makePublicallyAvailableOnBintray' is true")
    val makePublicallyAvailableOnBintray =
      settingKey[Boolean]("Indicates whether an artifact is public and should be distributed or private")
    val repoKey             = settingKey[String]("Artifactory repo key")
    val bintrayReleasesFolder = settingKey[String]("Bintray releases folder")
    val artifactDescription = settingKey[ArtifactDescription]("Artifact description")
  }

  import autoImport._

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    makePublicallyAvailableOnBintray := false,
    artifactDescription := ArtifactDescription.withCrossScalaVersion(
      org            = organization.value,
      name           = name.value,
      version        = version.value,
      scalaVersion   = scalaVersion.value,
      // sbtVersion needs to be resolved in the context of the pluginCrossBuild so it resolves correctly for cross-compiling sbt
      sbtVersion     = (sbtVersion in pluginCrossBuild).value,
      publicArtifact = makePublicallyAvailableOnBintray.value,
      sbtPlugin      = sbtPlugin.value,
      scalaJsVersion = if (isScalaJSProject.value) Some(ScalaJSCrossVersion.currentBinaryVersion) else None
    ),
    repoKey := artifactoryRepoKey(sbtPlugin.value, makePublicallyAvailableOnBintray.value),
    bintrayReleasesFolder := {
      val resolved = resolveBintrayReleasesFolder(maybeArtifactoryUri)
      sLog.value.info(s"SbtArtifactoryPlugin - Resolved bintray releases folder to be '$resolved' based on the artifactory URI")
      resolved
    },
    publishMavenStyle := !sbtPlugin.value,
    publishTo := maybeArtifactoryUri.map { uri =>
      if (sbtPlugin.value)
        Resolver.url(repoKey.value, url(s"$uri/${repoKey.value}"))(ivyStylePatterns)
      else
        "Artifactory Realm" at s"$uri/${repoKey.value}"
    },
    credentials ++= {
      streams.value.log.info(
        s"SbtArtifactoryPlugin - Configuring Artifactory... " +
          s"Host: ${maybeArtifactoryUri.getOrElse("not set")}. " +
          s"User: ${maybeArtifactoryUsername.getOrElse("not set")}. " +
          s"Password defined: ${maybeArtifactoryPassword.isDefined}")

      maybeArtifactoryCredentials.toSeq
    },
    unpublishFromArtifactory := {
      streams.value.log.info("SbtArtifactoryPlugin - Unpublishing from Artifactory...")
        artifactoryConnector(repoKey.value)
          .deleteVersion(artifactDescription.value, streams.value.log)
          .awaitResult
    },
    unpublishFromArtifactoryBintrayDistribution := {
      streams.value.log.info("SbtArtifactoryPlugin - Unpublishing from Artifactory bintray-distribution...")
      artifactoryConnector(repoKey.value)
        .deleteBintrayDistributionVersion(artifactDescription.value, bintrayReleasesFolder.value, streams.value.log)
        .awaitResult
    },
    unpublishFromBintray := {
      streams.value.log.info("SbtArtifactoryPlugin - Deleting from Bintray...")
        bintrayConnector(repoKey.value)
          .deleteReleaseOrPluginVersion(artifactDescription.value, bintrayReleasesFolder.value, streams.value.log)
          .awaitResult
    },
    unpublish := Def
      .sequential(
        unpublishFromArtifactory,
        unpublishFromArtifactoryBintrayDistribution,
        unpublishFromBintray
      ).value,
    distributeToBintray := {
      streams.value.log.info("SbtArtifactoryPlugin - Distributing to Bintray...")
        new BintrayDistributor(artifactoryConnector(repoKey.value), streams.value.log)
        .distributePublicArtifact(artifactDescription.value)
        .awaitResult
    },
    publishAndDistribute := {
      streams.value.log.info("Not distributing to Bintray...")
      publish.value
    }
  )

  override def trigger = allRequirements

  private[hmrc] def artifactoryRepoKey(sbtPlugin: Boolean, publicArtifact: Boolean): String =
    (sbtPlugin, publicArtifact) match {
      case (false, false) => "hmrc-releases-local"
      case (false, true)  => "hmrc-public-releases-local"
      case (true, false)  => "hmrc-sbt-plugin-releases-local"
      case (true, true)   => "hmrc-public-sbt-plugin-releases-local"
    }

  private[hmrc] def resolveBintrayReleasesFolder(artifactoryUriToResolve: Option[String]): String = {
    // Determine whether we're pointing at labs or live artifactory, and resolve to use the correct releases repo
    (for {
      artifactoryUri <- artifactoryUriToResolve
      labNum <- artifactoryLabsPattern.findFirstMatchIn(artifactoryUri).map(_.group(1))
    } yield s"releases-lab$labNum").getOrElse("releases")
  }

  private def artifactoryConnector = new ArtifactoryConnector(
    Http,
    directCredentials(
      getOrError(maybeArtifactoryUri, artifactoryUriEnvKey),
      getOrError(maybeArtifactoryUsername, artifactoryUsernameEnvKey),
      getOrError(maybeArtifactoryPassword, artifactoryPasswordEnvKey)
    ),
    _: String
  )

  private def bintrayConnector = new BintrayConnector(
    Http,
    directCredentials(
      "https://bintray.com",
      getOrError(maybeBintrayUsername, bintrayUsernameEnvKey),
      getOrError(maybeBintrayPassword, bintrayPasswordEnvKey)
    ),
    _: String
  )

  private def getOrError(option: Option[String], keyName: String) = option.getOrElse {
    sys.error(s"SbtArtifactoryPlugin - No $keyName environment variable found")
  }

  private def directCredentials(uri: String, userName: String, password: String): DirectCredentials =
    Credentials(
      realm    = "Artifactory Realm",
      host     = new URI(uri).getHost,
      userName = userName,
      passwd   = password
    ).asInstanceOf[DirectCredentials]

  private implicit class FutureOps[T](future: Future[T]) {
    def awaitResult: T = Await.result(future, distributionTimeout)
  }
}
