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

import _root_.bintray.BintrayPlugin
import bintray.BintrayKeys._
import org.scalajs.sbtplugin.ScalaJSCrossVersion
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.isScalaJSProject
import sbt.Classpaths.{getPublishTo, publishTask}
import sbt.Keys.{publish, sbtPlugin, _}
import sbt.Resolver.ivyStylePatterns
import sbt.{Def, PublishConfiguration, Resolver, taskKey, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.matching.Regex

object SbtArtifactory extends sbt.AutoPlugin{

  private val distributionTimeout = 1 minute

  private val artifactoryUriEnvKey            = "ARTIFACTORY_URI"
  private val artifactoryUsernameEnvKey       = "ARTIFACTORY_USERNAME"
  private val artifactoryPasswordEnvKey       = "ARTIFACTORY_PASSWORD"
  private lazy val maybeArtifactoryUri        = sys.env.get(artifactoryUriEnvKey)
  private lazy val maybeArtifactoryUsername   = sys.env.get(artifactoryUsernameEnvKey)
  private lazy val maybeArtifactoryPassword   = sys.env.get(artifactoryPasswordEnvKey)

  private lazy val maybeBintrayOrg            = sys.props.get("bintray.org")

  private val artifactoryLabsPattern: Regex = ".*lab([0-9]{2}).*".r

  private lazy val maybeArtifactoryCredentials = for {
    uri      <- maybeArtifactoryUri
    username <- maybeArtifactoryUsername
    password <- maybeArtifactoryPassword
  } yield directCredentials(uri, username, password)

  object autoImport {
    val unpublishFromArtifactory = taskKey[Unit]("Unpublish from Artifactory")
    val unpublishFromBintray = taskKey[Unit]("Unpublish from Bintray")
    val unpublish = taskKey[Unit]("Unpublish from Artifactory and from Bintray")
    val publishToBintray = taskKey[Unit]("Publish artifact to Artifactory")
    val publishToArtifactory = taskKey[Unit]("Publish artifact to Bintray")
    val publishAndDistribute = taskKey[Unit]("Deprecated - please use publishAll instead")
    val makePublicallyAvailableOnBintray = settingKey[Boolean]("Indicates whether an artifact is public and should be published to Bintray")
    val repoKey = settingKey[String]("Artifactory repository name")
    val artifactDescription = settingKey[ArtifactDescription]("Artifact description")
    val bintrayPublishConfiguration = taskKey[PublishConfiguration]("Configuration for publishing to a Bintray repository")
  }

  import autoImport._

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := !sbtPlugin.value,
    bintrayOrganization := maybeBintrayOrg.orElse(Some("hmrc")).map { org =>
      sLog.value.info(s"SbtArtifactoryPlugin - Resolved Bintray organisation to be '$org'")
      org
    },
    bintrayRepository := {
      val resolved = bintrayRepoKey(sbtPlugin.value, maybeArtifactoryUri)
      sLog.value.info(s"SbtArtifactoryPlugin - Resolved Bintray repository to be '$resolved'")
      resolved
    },
    bintrayPublishConfiguration := PublishConfigurationSupport.withResolverName(publishConfiguration.value, getPublishTo((publishTo in bintray).value).name),
    //Overriding the default otherResolvers is required to also initialise the publishTo in bintray resolver
    otherResolvers := Resolver.publishMavenLocal +: (publishTo.value.toVector ++ (publishTo in bintray).value.toVector),
    licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
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
    unpublishFromBintray := Def.taskDyn({
      if (artifactDescription.value.publicArtifact) {
        streams.value.log.info("SbtArtifactoryPlugin - Unpublishing from Bintray...")
        bintrayUnpublish.toTask
      } else {
        Def.task {
          streams.value.log.info("SbtArtifactoryPlugin - Nothing to unpublish from Bintray...")
        }
      }
    }).result.value,// fail silently
    unpublish := Def.sequential(
      unpublishFromArtifactory,
      unpublishFromBintray
    ).value,
    publishToArtifactory := publishTask(publishConfiguration, deliver).value,
    publishToBintray := Def.taskDyn({
      if(artifactDescription.value.publicArtifact) {
        streams.value.log.info("SbtArtifactoryPlugin - Publishing to Bintray...")
        bintrayRelease
          .dependsOn(publishTask(bintrayPublishConfiguration, deliver))
          .dependsOn(bintrayEnsureBintrayPackageExists, bintrayEnsureLicenses)
      } else {
        Def.task{
          streams.value.log.info("SbtArtifactoryPlugin - Not publishing to Bintray...")
        }
      }
    }).value,
    publish := Def.sequential(
      publishToArtifactory,
      publishToBintray
    ).value,
    publishAndDistribute := {
      sLog.value.info(s"SbtArtifactoryPlugin - 'publishAndDistribute' is deprecated. Please use publish instead")
      publish.value
    }
  )

  override def trigger = allRequirements
  //Without this the BintrayPlugin#bintrayRepository default definition may be applied after our definition
  override def requires: Plugins = BintrayPlugin

  private[hmrc] def artifactoryRepoKey(isSbtPlugin: Boolean, publicArtifact: Boolean): String =
    (isSbtPlugin, publicArtifact) match {
      case (false, false) => "hmrc-releases-local"
      case (false, true)  => "hmrc-public-releases-local"
      case (true, false)  => "hmrc-sbt-plugin-releases-local"
      case (true, true)   => "hmrc-public-sbt-plugin-releases-local"
    }

  private[hmrc] def bintrayRepoKey(isSbtPlugin: Boolean, artifactoryUriToResolve: Option[String]): String =
    if (isSbtPlugin) {
      "sbt-plugin-releases"
    } else {
      // Determine whether we're pointing at labs or live artifactory, and resolve to use the correct releases repo
      (for {
        artifactoryUri <- artifactoryUriToResolve
        labNum <- artifactoryLabsPattern.findFirstMatchIn(artifactoryUri).map(_.group(1))
      } yield s"releases-lab$labNum").getOrElse("releases")
    }

  private def artifactoryConnector(repositoryName: String) = new ArtifactoryConnector(
    DispatchCrossSupport.http,
    directCredentials(
      getOrError(maybeArtifactoryUri, artifactoryUriEnvKey),
      getOrError(maybeArtifactoryUsername, artifactoryUsernameEnvKey),
      getOrError(maybeArtifactoryPassword, artifactoryPasswordEnvKey)
    ),
    repositoryName
  )

  private def getOrError(option: Option[String], keyName: String): String = option.getOrElse {
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
