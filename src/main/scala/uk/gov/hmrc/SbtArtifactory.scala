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

import sbt.Keys._
import sbt._
import scala.util.{Failure, Success}

object SbtArtifactory extends sbt.AutoPlugin {

  override def trigger = allRequirements

  private lazy val uri            = findEnvVariable("ARTIFACTORY_URI")
  private lazy val username       = findEnvVariable("ARTIFACTORY_USERNAME")
  private lazy val password       = findEnvVariable("ARTIFACTORY_PASSWORD")
  private lazy val repositoryName = findEnvVariable("REPOSITORY_NAME")
  private lazy val artifactoryCredentials: DirectCredentials =
    Credentials(
      realm    = "Artifactory Realm",
      host     = new URI(uri).getHost,
      userName = username,
      passwd   = password
    ).asInstanceOf[DirectCredentials]

  private def findEnvVariable(key: String): String =
    sys.env.getOrElse(key, sys.error(s"'$key' environment variable not set"))

  object autoImport {
    val unpublish = taskKey[Unit]("artifactory_unpublish")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    publishTo := Some("Artifactory Realm" at uri + "/" + repositoryName),
    credentials += artifactoryCredentials,
    unpublish := {
      val (major, minor) = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((mj, mn)) => mj -> mn
        case _              => throw new Exception(s"Unable to extract Scala version from ${scalaVersion.value}")
      }

      unpublishArtifact(
        streams.value.log,
        artifactoryCredentials,
        organization.value,
        name.value,
        version.value,
        s"$major.$minor"
      )
    }
  )

  def unpublishArtifact(
    logger: Logger,
    credentials: DirectCredentials,
    org: String,
    name: String,
    version: String,
    scalaVersion: String
  ): Unit = {
    val sbtArtifactoryRepo = new ArtifactoryRepo(credentials, repositoryName) {
      override def log(msg: String): Unit = logger.info(msg)
    }

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
}
