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

import com.ning.http.client.Response
import dispatch.{Http, Req}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => is}
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.mockito.MockitoSugar
import sbt.{Credentials, DirectCredentials, Logger}
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.util.Success

class ArtifactVersionSpec extends FlatSpec {

  "ArtifactVersion.path" should "be formed using pattern: 'org/name_scalaVersion/version'" in {
    ArtifactVersion("2.11", "org", "my-artifact", "0.1.0").path shouldBe "org/my-artifact_2.11/0.1.0"
  }

  "ArtifactVersion.path" should "be formed using pattern: 'org/name_scalaVersion/version' - case when org contains dots" in {
    ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0").path shouldBe "uk/gov/hmrc/my-artifact_2.11/0.1.0"
  }

  "ArtifactVersion.path" should "be formed using pattern: 'org/name_scalaVersion/version' - case when artifact-name contains dots" in {
    ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact.public", "0.1.0").path shouldBe "uk/gov/hmrc/my-artifact.public_2.11/0.1.0"
  }
}

class ArtifactoryRepoSpec extends FlatSpec with MockitoSugar {

  "deleteVersion" should "use the proper authentication" in new DeleteVersionSetup {

    when(response.getStatusCode).thenReturn(204)

    repo.deleteVersion(artifactVersion)

    val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
    verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

    val request = reqCaptor.getValue.toRequest
    request.getUrl                                    shouldBe s"https://${artifactoryCredentials.host}/artifactory/$repositoryName/${artifactVersion.path}/"
    request.getMethod                                 shouldBe "DELETE"
    request.getHeaders.getFirstValue("Authorization") shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
  }

  it should "return successfully if it deletes the artifact" in new DeleteVersionSetup {

    when(response.getStatusCode).thenReturn(204)

    repo.deleteVersion(artifactVersion) shouldBe
      Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' deleted successfully from localhost")
  }

  it should "return successfully with a proper message if the artifact doesn't exist" in new DeleteVersionSetup {

    when(response.getStatusCode).thenReturn(404)

    repo.deleteVersion(artifactVersion) shouldBe
      Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' not found on localhost. No action taken.")
  }

  it should "return a failure when the delete API call returns an unexpected result" in new DeleteVersionSetup {

    when(response.getStatusCode).thenReturn(401)

    repo.deleteVersion(artifactVersion).isFailure shouldBe true
    repo.deleteVersion(artifactVersion).failed.get.getMessage shouldBe
      "Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' could not be deleted from localhost. Received status 401"
  }

  private trait DeleteVersionSetup {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "password"
    ).asInstanceOf[DirectCredentials]
    val artifactVersion = ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")

    val httpClient = mock[Http]
    val logger     = mock[Logger]

    val response = mock[Response]
    when(httpClient(any[Req])(is(executionContext))).thenReturn(Future.successful(response))

    val repositoryName = "hmrc-releases-local"
    val repo           = new ArtifactoryRepo(httpClient, logger, artifactoryCredentials, repositoryName)
  }

    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).isFailure shouldBe true
    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).failed.get.getMessage shouldBe
      "Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' could not be deleted from localhost. Received status 401"
  }

}
