/*
 * Copyright 2018 HM Revenue & Customs
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
import sbt.Keys._
import sbt._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object SbtArtifactory extends sbt.AutoPlugin {

  override def trigger = allRequirements

  private val distributionTimeout = 1 minute

  private val uriEnvKey                = "ARTIFACTORY_URI"
  private val repositoryNameEnvKey     = "REPOSITORY_NAME"
  private val usernameEnvKey           = "ARTIFACTORY_USERNAME"
  private val passwordEnvKey           = "ARTIFACTORY_PASSWORD"
  private lazy val maybeUri            = findEnvVariable(uriEnvKey)
  private lazy val maybeUsername       = findEnvVariable(usernameEnvKey)
  private lazy val maybePassword       = findEnvVariable(passwordEnvKey)
  private lazy val maybeRepositoryName = findEnvVariable(repositoryNameEnvKey)
  private lazy val maybeArtifactoryCredentials = for {
    uri      <- maybeUri
    username <- maybeUsername
    password <- maybePassword
  } yield directCredentials(uri, username, password)

  private def artifactoryCredentials = directCredentials(
    getOrError(maybeUri, repositoryNameEnvKey),
    getOrError(maybeUsername, usernameEnvKey),
    getOrError(maybePassword, passwordEnvKey)
  )

  object autoImport {
    val unpublish           = taskKey[Unit]("artifactory_unpublish")
    val distributeToBintray = taskKey[Unit]("distribute_to_bintray")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := { if (sbtPlugin.value) false else publishMavenStyle.value },
    publishTo := maybePublishToResolver,
    credentials ++= maybeArtifactoryCredentials.toSeq,
    unpublish := {
      unpublishArtifact(
        streams.value.log,
        artifact(organization.value, name.value, version.value, scalaVersion.value)
      )
    },
    distributeToBintray := {
      val logger = streams.value.log
      val artifactoryConnector = new ArtifactoryConnector(
        Http,
        logger,
        artifactoryCredentials,
        getOrError(maybeRepositoryName, repositoryNameEnvKey)
      )
      val bintrayDistributor = new BintrayDistributor(artifactoryConnector, logger)

      Await.result(
        bintrayDistributor.distribute(artifact(organization.value, name.value, version.value, scalaVersion.value)),
        atMost = distributionTimeout
      )
    }
  )

  private def artifact(organization: String, name: String, version: String, scalaVersion: String) = {
    val crossScalaVersion = CrossVersion.partialVersion(scalaVersion) match {
      case Some((major, minor)) => s"$major.$minor"
      case _                    => throw new Exception(s"Unable to extract Scala version from $scalaVersion")
    }
    ArtifactVersion(
      scalaVersion = crossScalaVersion,
      org          = organization,
      name         = name,
      version      = version
    )
  }

  private lazy val maybePublishToResolver = for {
    uri            <- maybeUri
    repositoryName <- maybeRepositoryName
  } yield "Artifactory Realm" at uri + "/" + repositoryName

  private def unpublishArtifact(
    logger: Logger,
    artifact: ArtifactVersion
  ) = {
    val artifactoryConnector = new ArtifactoryConnector(
      Http,
      logger,
      artifactoryCredentials,
      getOrError(maybeRepositoryName, repositoryNameEnvKey)
    )

    logger.info(s"Deleting artifact: $artifact")

    artifactoryConnector.deleteVersion(artifact) match {
      case Success(message)   => logger.info(message)
      case Failure(exception) => logger.error(exception.getMessage)
    }
  }

  private def findEnvVariable(key: String): Option[String] = sys.env.get(key)

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
}
