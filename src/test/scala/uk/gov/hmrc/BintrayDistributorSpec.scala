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

import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import sbt.Logger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class BintrayDistributorSpec extends WordSpec with MockitoSugar {

  "distributeToBintray" should {
    "fetch list of artifacts paths and distribute them to Bintray" in new Setup {
      val artifactVersion = ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")

      val artifactsPaths = Seq("path1", "path2")
      when(artifactoryConnector.fetchArtifactsPaths(artifactVersion))
        .thenReturn(Future.successful(artifactsPaths))
      when(artifactoryConnector.distributeToBintray(artifactsPaths))
        .thenReturn(Future.successful("a message"))

      Await.result(bintrayDistributor.distribute(artifactVersion), Duration.Inf)

      verify(artifactoryConnector).fetchArtifactsPaths(artifactVersion)
      verify(artifactoryConnector).distributeToBintray(artifactsPaths)
      verifyNoMoreInteractions(artifactoryConnector)
    }
  }

  private trait Setup {
    val artifactoryConnector = mock[ArtifactoryConnector]
    val logger               = mock[Logger]
    val bintrayDistributor   = new BintrayDistributor(artifactoryConnector, logger)
  }
}
