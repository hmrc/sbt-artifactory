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

import java.util.Base64

import dispatch.Defaults._
import dispatch._
import sbt.{DirectCredentials, Logger}

class BintrayConnector(httpClient: Http, credentials: DirectCredentials, repositoryName: String) {

  private val encodedCredentials: String =
    Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())

  def deleteReleaseOrPluginVersion(artifact: ArtifactDescription, logger: Logger): Future[Unit] = {

    val bintrayReleasesUrl =
      s"https://${credentials.host}/api/v1/packages/hmrc/releases/${artifact.name}/versions/${artifact.version}"
    val labsBintrayReleasesUrl =
      s"https://${credentials.host}/api/v1/packages/hmrc/releases-lab03/${artifact.name}/versions/${artifact.version}"
    val bintrayPluginsUrl =
      s"https://${credentials.host}/api/v1/packages/hmrc/sbt-plugin-releases/${artifact.name}/versions/${artifact.version}"

    for {
      // The artifact will only be in max one of these locations, other locations will just skip and log: 'No action taken.'
      _ <- deleteVersion(bintrayReleasesUrl, artifact, logger)
      _ <- deleteVersion(labsBintrayReleasesUrl, artifact, logger)
      _ <- deleteVersion(bintrayPluginsUrl, artifact, logger)
    } yield ()
  }

  private def deleteVersion(bintrayUri: String, artifact: ArtifactDescription, logger: Logger): Future[Unit] = {
    logger.info(s"Attempting to delete artifact '$artifact' from $bintrayUri")
    httpClient(url(bintrayUri).DELETE <:< Seq("Authorization" -> s"Basic $encodedCredentials"))
      .map(_.getStatusCode)
      .map {
        case 200 | 204 =>
          logger.info(s"Artifact '$artifact' deleted successfully from $bintrayUri")
        case 404 =>
          logger.info(s"Artifact '$artifact' not found at $bintrayUri. No action taken.")
        case status =>
          logger.info(s"Artifact '$artifact' could not be deleted from $bintrayUri. Received status $status")
      }
  }
}
