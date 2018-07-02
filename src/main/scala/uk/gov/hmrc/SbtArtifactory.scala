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
import scala.util.{Failure, Success}

object SbtArtifactory extends sbt.AutoPlugin {

  override def trigger = allRequirements

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

  object autoImport {
    val unpublish = taskKey[Unit]("artifactory_unpublish")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := { if (sbtPlugin.value) false else publishMavenStyle.value },
    publishTo := maybePublishToResolver,
    credentials ++= maybeArtifactoryCredentials.toSeq,
    unpublish := {
      val (major, minor) = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((mj, mn)) => mj -> mn
        case _              => throw new Exception(s"Unable to extract Scala version from ${scalaVersion.value}")
      }

      unpublishArtifact(
        streams.value.log,
        organization.value,
        name.value,
        version.value,
        s"$major.$minor"
      )
    }
  )

  private lazy val maybePublishToResolver = for {
    uri            <- maybeUri
    repositoryName <- maybeRepositoryName
  } yield "Artifactory Realm" at uri + "/" + repositoryName

  def unpublishArtifact(
    logger: Logger,
    org: String,
    name: String,
    version: String,
    scalaVersion: String
  ): Unit = {

    val artifactoryCredentials = directCredentials(
      getOrError(maybeUri, repositoryNameEnvKey),
      getOrError(maybeUsername, usernameEnvKey),
      getOrError(maybePassword, passwordEnvKey)
    )

    val sbtArtifactoryRepo = new ArtifactoryRepo(
      Http,
      logger,
      artifactoryCredentials,
      getOrError(maybeRepositoryName, repositoryNameEnvKey)
    )

    logger.info(s"Deleting artifact: $org.$name.${version}_$scalaVersion")

    val artifact = ArtifactVersion(
      scalaVersion = scalaVersion,
      org          = org,
      name         = name,
      version      = version
    )

    sbtArtifactoryRepo.deleteVersion(artifact) match {
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
