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

import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import sbt.Logger

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random

class BintrayDistributorSpec extends WordSpec with MockitoSugar {

  "distributeToBintray" should {

    "fetch list of artifacts paths and distribute them to Bintray for public artifacts" in new Setup {
      val artifactDescription = ArtifactDescription.withCrossScalaVersion(
        org            = "uk.gov.hmrc",
        name           = "my-artifact",
        version        = "0.1.0",
        scalaVersion   = "2.11",
        sbtVersion     = "0.13.17",
        publicArtifact = true,
        sbtPlugin      = Random.nextBoolean(),
        scalaJsVersion = Some("0.6.26")
      )

      val artifactsPaths = Set("path1", "path2")
      when(artifactoryConnector.fetchArtifactsPaths(artifactDescription))
        .thenReturn(Future.successful(artifactsPaths))
      when(artifactoryConnector.distributeToBintray(artifactsPaths))
        .thenReturn(Future.successful("a message"))

      Await.result(bintrayDistributor.distributePublicArtifact(artifactDescription), Duration.Inf)

      verify(artifactoryConnector).fetchArtifactsPaths(artifactDescription)
      verify(artifactoryConnector).distributeToBintray(artifactsPaths)
      verifyNoMoreInteractions(artifactoryConnector)
    }

    "do nothing for private artifacts" in new Setup {
      val artifactDescription = ArtifactDescription.withCrossScalaVersion(
        org            = "uk.gov.hmrc",
        name           = "my-artifact",
        version        = "0.1.0",
        scalaVersion   = "2.11",
        sbtVersion     = "0.13.17",
        publicArtifact = false,
        sbtPlugin      = Random.nextBoolean(),
        scalaJsVersion = None
      )

      Await.result(bintrayDistributor.distributePublicArtifact(artifactDescription), Duration.Inf)

      verifyZeroInteractions(artifactoryConnector)
    }
  }

  private trait Setup {
    val artifactoryConnector = mock[ArtifactoryConnector]
    val logger               = mock[Logger]
    val bintrayDistributor   = new BintrayDistributor(artifactoryConnector, logger)
  }
}
