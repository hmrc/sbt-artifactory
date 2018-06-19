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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import sbt.{Credentials, DirectCredentials}
import scala.concurrent.Future

class ArtifactoryRepoSpec extends FlatSpec with Matchers with ScalaFutures with MockitoSugar {
  "artifactExists" should "return true if artifact exists and provide the correct credentials" in {

    val artifactoryCredentials: DirectCredentials = Credentials(
      realm = "Artifactory Realm",
      host = "http://localhost",
      userName = "username",
      passwd = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(200)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = {
        request.toRequest.getHeaders.get("Authorization").get(0) shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
        Future.successful(response)
      }

      override def log(msg: String): Unit = {}
    }


    repo.artifactExists(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).futureValue shouldBe true

  }

  it should "return false if the artifact doesn't exist" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm = "Artifactory Realm",
      host = "http://localhost",
      userName = "username",
      passwd = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(404)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}
    }

    repo.artifactExists(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).futureValue shouldBe false

  }

  "deleteVersion" should "return a successful future if it deletes the artifact" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm = "Artifactory Realm",
      host = "http://localhost",
      userName = "username",
      passwd = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(204)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}
    }
    repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).futureValue shouldBe response
  }

    it should "return a failed future if it fails to delete the artifact" in {
      val artifactoryCredentials: DirectCredentials = Credentials(
        realm = "Artifactory Realm",
        host = "http://localhost",
        userName = "username",
        passwd = "password"
      ).asInstanceOf[DirectCredentials]

      val exception = new RuntimeException

      val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
        override def execute(request: Req): Future[Response] = Future.failed(exception)

        override def log(msg: String): Unit = {}
      }


        repo.deleteVersion(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).failed.futureValue shouldBe exception
    }

  "deleteSafe" should "return a successful future if it deletes the artifact" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm = "Artifactory Realm",
      host = "http://localhost",
      userName = "username",
      passwd = "password"
    ).asInstanceOf[DirectCredentials]

    val response = mock[Response]
    Mockito.when(response.getStatusCode).thenReturn(204)

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = Future.successful(response)

      override def log(msg: String): Unit = {}

      override def artifactExists(artifact: ArtifactVersion): Future[Boolean] = Future.successful(true)
    }
    repo.deleteSafe(ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")).futureValue shouldBe response
  }

  "deleteSafe" should "return a failed future if it fails to delete the artifact because it is not found" in {
    val artifactoryCredentials: DirectCredentials = Credentials(
      realm = "Artifactory Realm",
      host = "http://localhost",
      userName = "username",
      passwd = "password"
    ).asInstanceOf[DirectCredentials]

    val artifactVersion = ArtifactVersion("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0")

    val exception = new RuntimeException(s"Artifact $artifactVersion does not exist.")

    val repo = new ArtifactoryRepo(artifactoryCredentials, "hmrc-releases-local") {
      override def execute(request: Req): Future[Response] = ???

      override def log(msg: String): Unit = {}

      override def artifactExists(artifact: ArtifactVersion): Future[Boolean] = Future.successful(false)
    }
    repo.deleteSafe(artifactVersion).failed.futureValue.getMessage shouldBe exception.getMessage
  }

}
