/*
 * Copyright 2019 HM Revenue & Customs
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

import dispatch.Http
import org.scalajs.sbtplugin.ScalaJSCrossVersion
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.isScalaJSProject
import sbt.Keys._
import sbt.Resolver.ivyStylePatterns
import sbt._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object SbtArtifactory extends sbt.AutoPlugin {

  private val distributionTimeout = 1 minute

  private val uriEnvKey          = "ARTIFACTORY_URI"
  private val usernameEnvKey     = "ARTIFACTORY_USERNAME"
  private val passwordEnvKey     = "ARTIFACTORY_PASSWORD"
  private lazy val maybeUri      = sys.env.get(uriEnvKey)
  private lazy val maybeUsername = sys.env.get(usernameEnvKey)
  private lazy val maybePassword = sys.env.get(passwordEnvKey)
  private lazy val maybeArtifactoryCredentials = for {
    uri      <- maybeUri
    username <- maybeUsername
    password <- maybePassword
  } yield directCredentials(uri, username, password)

  object autoImport {
    val unpublish = taskKey[Unit]("Unpublish from Artifactory.")
    val distributeToBintray =
      taskKey[Unit]("Distributes artifacts from an Artifactory Distribution Repository to Bintray")
    val publishAndDistribute =
      taskKey[Unit]("Publish to Artifactory and distribute to Bintray if 'makePublicallyAvailableOnBintray' is true")
    val makePublicallyAvailableOnBintray =
      settingKey[Boolean]("Indicates whether an artifact is public and should be distributed or private")
    val repoKey             = settingKey[String]("Artifactory repo key")
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
      sbtVersion     = sbtVersion.value,
      publicArtifact = makePublicallyAvailableOnBintray.value,
      sbtPlugin      = sbtPlugin.value,
      scalaJsVersion = if (isScalaJSProject.value) Some(ScalaJSCrossVersion.currentBinaryVersion) else None
    ),
    repoKey := artifactoryRepoKey(sbtPlugin.value, makePublicallyAvailableOnBintray.value),
    publishMavenStyle := !sbtPlugin.value,
    publishTo := maybeUri.map { uri =>
      if (sbtPlugin.value)
        Resolver.url(repoKey.value, url(s"$uri/${repoKey.value}"))(ivyStylePatterns)
      else
        "Artifactory Realm" at s"$uri/${repoKey.value}"
    },
    credentials ++= {
      streams.value.log.info(
        s"Configuring Artifactory... " +
          s"Host: ${maybeUri.getOrElse("not set")}. " +
          s"User: ${maybeUsername.getOrElse("not set")}. " +
          s"Password defined: ${maybePassword.isDefined}")

      maybeArtifactoryCredentials.toSeq
    },
    unpublish :=
      streams.value.log.info {
        artifactoryConnector(repoKey.value)
          .deleteVersion(artifactDescription.value)
          .awaitResult
      },
    distributeToBintray :=
      new BintrayDistributor(artifactoryConnector(repoKey.value), streams.value.log)
        .distributePublicArtifact(artifactDescription.value)
        .awaitResult,
    publishAndDistribute := Def
      .sequential(
        publish,
        distributeToBintray
      )
      .value
  )

  private[hmrc] def artifactoryRepoKey(sbtPlugin: Boolean, publicArtifact: Boolean): String =
    (sbtPlugin, publicArtifact) match {
      case (false, false) => "hmrc-releases-local"
      case (false, true)  => "hmrc-public-releases-local"
      case (true, false)  => "hmrc-sbt-plugin-releases-local"
      case (true, true)   => "hmrc-public-sbt-plugin-releases-local"
    }

  private def artifactoryConnector = new ArtifactoryConnector(
    Http,
    directCredentials(
      getOrError(maybeUri, uriEnvKey),
      getOrError(maybeUsername, usernameEnvKey),
      getOrError(maybePassword, passwordEnvKey)
    ),
    _: String
  )

  private def getOrError(option: Option[String], keyName: String) = option.getOrElse {
    sys.error(s"No $keyName environment variable found")
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
