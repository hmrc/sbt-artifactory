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
import sbt.{Def, PublishConfiguration, Resolver, taskKey, _}
import uk.gov.hmrc.sbtartifactory.BuildInfo

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.matching.Regex

object SbtArtifactory extends sbt.AutoPlugin {

  private val distributionTimeout = 1.minute

  private val artifactoryUriEnvKey            = "ARTIFACTORY_URI"
  private val artifactoryUsernameEnvKey       = "ARTIFACTORY_USERNAME"
  private val artifactoryPasswordEnvKey       = "ARTIFACTORY_PASSWORD"
  private lazy val maybeArtifactoryUri        = sys.env.get(artifactoryUriEnvKey)
  private lazy val maybeArtifactoryUsername   = sys.env.get(artifactoryUsernameEnvKey)
  private lazy val maybeArtifactoryPassword   = sys.env.get(artifactoryPasswordEnvKey)

  private lazy val maybeBintrayOrg            = sys.props.get("bintray.org")
  private lazy val suppressBintrayError       = sys.props.get("bintray.suppressError").map(_ == "true").getOrElse(false)

  private val artifactoryLabsPattern: Regex = ".*lab([0-9]{2}).*".r

  private def maybeArtifactoryCredentials =
    for {
      uri      <- maybeArtifactoryUri
      username <- maybeArtifactoryUsername
      password <- maybeArtifactoryPassword
    } yield directCredentials(uri, username, password)

  object autoImport {
    val makePublicallyAvailableOnBintray = settingKey[Boolean]("Indicates whether an artifact is public and should be published to Bintray")
    val repoKey                          = settingKey[String]("Artifactory repository name")
    val artifactDescription              = settingKey[ArtifactDescription]("Artifact description")

    val unpublishFromArtifactory         = taskKey[Unit]("Unpublish from Artifactory")
    val unpublishFromBintray             = taskKey[Unit]("Unpublish from Bintray")
    val unpublish                        = taskKey[Unit]("Unpublish from Artifactory and from Bintray")
    val publishToBintray                 = taskKey[Unit]("Publish artifact to Artifactory")
    val publishToArtifactory             = taskKey[Unit]("Publish artifact to Bintray")
    val publishAndDistribute             = taskKey[Unit]("Deprecated - please use publishAll instead")
    val bintrayPublishConfiguration      = taskKey[PublishConfiguration]("Configuration for publishing to a Bintray repository")
  }

  import autoImport._

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle   := !sbtPlugin.value,
    bintrayOrganization := maybeBintrayOrg.orElse(Some("hmrc")),
    bintrayRepository   := bintrayRepoKey(sbtPlugin.value, maybeArtifactoryUri),
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
      val pattern = if (sbtPlugin.value)
                      Resolver.ivyStylePatterns
                    else
                      Resolver.mavenStylePatterns
      Resolver.url(repoKey.value, url(s"$uri/${repoKey.value}"))(pattern)
    },
    credentials ++= {
      val credString = // DirectCredentials.toString is not implemented for sbt 0.13
                       maybeArtifactoryCredentials.map(cred => s"DirectCredentials(${cred.realm}, ${cred.host}, ${cred.userName}, ****)")
      sLog.value.info(msg(name.value, s"Configuring Artifactory credentials: $credString"))
      maybeArtifactoryCredentials.toSeq
    },
    unpublishFromArtifactory := {
      sLog.value.info(msg(name.value, "Unpublishing from Artifactory..."))
      artifactoryConnector(repoKey.value)
        .deleteVersion(artifactDescription.value, sLog.value)
        .awaitResult
    },
    unpublishFromBintray := Def.taskDyn {
      if (artifactDescription.value.publicArtifact) {
        sLog.value.info(msg(name.value, s"Unpublishing from Bintray (repository: ${bintrayRepository.value}, organisation: ${bintrayOrganization.value})..."))
        bintrayUnpublish.toTask
          //don't propagate exception
          .result.value.toEither.fold(
            incomplete => Def.task { sLog.value.warn(msg(name.value, s"Failed to unpublish from Bintray:\n $incomplete")) },
            r          => Def.task { r }
          )
      } else
        Def.task {
          sLog.value.info(msg(name.value, s"Nothing to unpublish from Bintray..."))
        }
    }.value,
    unpublish := Def.sequential(
      unpublishFromArtifactory,
      unpublishFromBintray
    ).value,
    publishToArtifactory := {
      sLog.value.info(msg(name.value, "Publishing to Artifactory..."))
      publishTask(publishConfiguration, deliver).value
    },
    publishToBintray := Def.taskDyn {
      if (artifactDescription.value.publicArtifact) {
        sLog.value.info(msg(name.value, s"Publishing to Bintray (repository: ${bintrayRepository.value}, organisation: ${bintrayOrganization.value})..."))
        bintrayRelease
          .dependsOn(publishTask(bintrayPublishConfiguration, deliver))
          .dependsOn(bintrayEnsureBintrayPackageExists, bintrayEnsureLicenses)
          .result.value.toEither.fold(
            incomplete => Def.task {
                            if (suppressBintrayError)
                              sLog.value.warn(msg(name.value, s"Failed to publish to Bintray:\n $incomplete"))
                            else throw incomplete
                          },
            _          => Def.task { () }
          )
      } else
        Def.task {
          sLog.value.info(msg(name.value, s"Not publishing to Bintray..."))
        }
    }.value,
    publish := Def.sequential(
      publishToArtifactory,
      publishToBintray
    ).value,
    publishAndDistribute := {
      sLog.value.info(msg(name.value, s"'publishAndDistribute' is deprecated. Please use publish instead"))
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
         labNum         <- artifactoryLabsPattern.findFirstMatchIn(artifactoryUri).map(_.group(1))
       } yield s"releases-lab$labNum"
      ).getOrElse("releases")
    }

  private def artifactoryConnector(repositoryName: String) = new ArtifactoryConnector(
    DispatchCrossSupport.http,
    directCredentials(
      uri      = getOrError(maybeArtifactoryUri, artifactoryUriEnvKey),
      userName = getOrError(maybeArtifactoryUsername, artifactoryUsernameEnvKey),
      password = getOrError(maybeArtifactoryPassword, artifactoryPasswordEnvKey)
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

  def msg(projectName: String, msg: String): String =
    s"SbtArtifactoryPlugin [${BuildInfo.version}] ($projectName) - $msg"

  private implicit class FutureOps[T](future: Future[T]) {
    def awaitResult: T = Await.result(future, distributionTimeout)
  }
}
