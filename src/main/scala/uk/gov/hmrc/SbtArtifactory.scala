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

import sbt.Keys.{sbtPlugin, _}
import sbt._

import scala.util.{Failure, Success}

object SbtArtifactory extends sbt.AutoPlugin {

  val uri      = sys.env.getOrElse("ARTIFACTORY_URI", "")
  val host     = new URI(uri).getHost
  val username = sys.env.getOrElse("ARTIFACTORY_USERNAME", "")
  val password = sys.env.getOrElse("ARTIFACTORY_PASSWORD", "")

  def repository(sbtPlugin: Boolean) = if (sbtPlugin) "hmrc-sbt-plugin-releases-local" else "hmrc-releases-local"

  object autoImport {
    val unpublish = taskKey[Unit]("artifactory_unpublish")
  }

  import autoImport._

  val artifactoryCredentials: DirectCredentials = Credentials(
    realm    = "Artifactory Realm",
    host     = host,
    userName = username,
    passwd   = password
  ).asInstanceOf[DirectCredentials]

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      publishTo := { if (uri.isEmpty) None else Some("Artifactory Realm" at uri + "/" + repository(sbtPlugin.value)) },
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
          s"$major.$minor",
          sbtPlugin.value
        )
      }
    )

  def unpublishArtifact(
    logger: Logger,
    credentials: DirectCredentials,
    org: String,
    name: String,
    version: String,
    scalaVersion: String,
    sbtPlugin: Boolean
  ): Unit = {

    val sbtArtifactoryRepo = new ArtifactoryRepo(credentials, repository(sbtPlugin)) {
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
