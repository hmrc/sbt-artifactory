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

import sbt.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BintrayDistributor(artifactoryConnector: ArtifactoryConnector, logger: Logger) {

  def distributePublicArtifact(artifact: ArtifactDescription): Future[Unit] =
    if (artifact.publicArtifact)
      for {
        artifactsPaths <- artifactoryConnector.fetchArtifactsPaths(artifact)
        _ = logFetchedArtifactsPaths(artifact, artifactsPaths)
        distributionResult <- artifactoryConnector.distributeToBintray(artifactsPaths)
        _ = logger.info(distributionResult)
      } yield ()
    else {
      logger.info(s"$artifact is private. Nothing to distribute to Bintray")
      Future.successful(())
    }

  private def logFetchedArtifactsPaths(artifact: ArtifactDescription, paths: Set[String]): Unit =
    if (paths.isEmpty)
      logger.warn(s"No paths found in Artifactory for $artifact")
    else {
      logger.info(s"Artifacts paths for $artifact to be distributed to Bintray:")
      paths.foreach(logger.info(_))
    }
}
