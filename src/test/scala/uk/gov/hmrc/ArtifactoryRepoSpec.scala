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
import dispatch.Req
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import sbt.{Credentials, DirectCredentials}
import scala.concurrent.Future
import scala.util.Success

class ArtifactoryRepoSpec extends FlatSpec with Matchers with MockitoSugar {
  "deleteVersion" should "use the proper authentication" in {

    val artifactoryCredentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(204)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = {
        request.toRequest.getHeaders.get("Authorization").get(0) shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
        Future.successful(response)
      }

      override def log(msg: String): Unit = {}
    }

    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0"))

  }

  it should "return successfully if it deletes the artifact" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(204)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}
    }
    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")) shouldBe
      Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' deleted successfully from localhost")
  }

  it should "return successfully with a proper message if the artifact doesn't exist" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(404)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}
    }

    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")) shouldBe
      Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' not found on localhost. No action taken.")
  }

  it should "return a failure when the delete API call returns an unexpected result" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "WRONG_PASSWORD"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(401)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}
    }

    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).isFailure shouldBe true
    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).failed.get.getMessage shouldBe
      "Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' could not be deleted from localhost. Received status 401"
  }

}
