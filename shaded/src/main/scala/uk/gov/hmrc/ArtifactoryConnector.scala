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

class ArtifactoryConnector(httpClient: Http, credentials: DirectCredentials, repositoryName: String) {

  private val encodedCredentials: String =
    Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())

  def deleteVersion(artifact: ArtifactDescription, logger: Logger, repository: String = repositoryName): Future[Unit] = {
    val artifactUrl = s"https://${credentials.host}/artifactory/$repository/${artifact.path}/"

    logger.info(s"Attempting to delete artifact '$artifact' in repository '$repository', full path: $artifactUrl")

    httpClient(url(artifactUrl).DELETE.addHeader("Authorization", s"Basic $encodedCredentials"))
      .map(_.getStatusCode)
      .map {
        case 200 | 204 =>
          logger.info(s"Artifact '$artifact' deleted successfully from $artifactUrl")
        case 404 =>
          logger.info(s"Artifact '$artifact' not found on $artifactUrl. No action taken.")
        case status =>
          throw new RuntimeException(s"Artifact '$artifact' could not be deleted from $artifactUrl. Received status $status")
      }
  }
}
