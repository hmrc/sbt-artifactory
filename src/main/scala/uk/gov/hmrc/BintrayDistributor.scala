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

import sbt.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BintrayDistributor(artifactoryRepo: ArtifactoryConnector, logger: Logger) {

  def distribute(artifact: ArtifactVersion): Future[Unit] =
    for {
      artifactsPaths <- artifactoryRepo.fetchArtifactsPaths(artifact)
      _ = logFetchedArtifactsPaths(artifact, artifactsPaths)
      distributionResult <- artifactoryRepo.distributeToBintray(artifactsPaths)
      _ = logger.info(distributionResult)
    } yield ()

  private def logFetchedArtifactsPaths(artifact: ArtifactVersion, paths: Seq[String]): Unit =
    if (paths.isEmpty)
      logger.info(s"No $artifact artifacts paths found")
    else {
      logger.info(s"Found $artifact artifacts paths:")
      paths.foreach(logger.info(_))
    }
}
