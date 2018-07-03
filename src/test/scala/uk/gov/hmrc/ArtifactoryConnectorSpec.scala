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
import org.scalatest.{FlatSpec, WordSpec}
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import sbt.{Credentials, DirectCredentials, Logger}
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

class ArtifactVersionSpec extends FlatSpec with ScalaFutures {

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

class ArtifactoryConnectorSpec extends WordSpec with MockitoSugar {

  "deleteVersion" should {

    "send a delete request with proper authorization header" in new Setup {

      when(response.getStatusCode).thenReturn(204)

      repo.deleteVersion(artifactVersion)

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl                                    shouldBe s"https://${credentials.host}/artifactory/$repositoryName/${artifactVersion.path}/"
      request.getMethod                                 shouldBe "DELETE"
      request.getHeaders.getFirstValue("Authorization") shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
    }

    "return successfully if it deletes the artifact" in new Setup {

      when(response.getStatusCode).thenReturn(204)

      repo.deleteVersion(artifactVersion) shouldBe
        Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' deleted successfully from localhost")
    }

    "return successfully with a proper message if the artifact doesn't exist" in new Setup {

      when(response.getStatusCode).thenReturn(404)

      repo.deleteVersion(artifactVersion) shouldBe
        Success("Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' not found on localhost. No action taken.")
    }

    "return a failure when the delete API call returns an unexpected result" in new Setup {

      when(response.getStatusCode).thenReturn(401)

      repo.deleteVersion(artifactVersion).isFailure shouldBe true
      repo.deleteVersion(artifactVersion).failed.get.getMessage shouldBe
        "Artifact 'uk/gov/hmrc/my-artifact_2.11/0.1.0' could not be deleted from localhost. Received status 401"
    }
  }

  "fetchArtifactsPaths" should {
    "send a get request to fetch the list of paths to artifacts" in new Setup {
      when(response.getStatusCode).thenReturn(200)

      repo.fetchArtifactsPaths(artifactVersion)

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl    shouldBe s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifactVersion.path}"
      request.getMethod shouldBe "GET"
    }

    "return a list of paths to artifacts fetched from Artifactory" in new Setup {
      when(response.getStatusCode).thenReturn(200)

      val repoName = "repo-name"
      val path     = "/uk/gov/hmrc/artifact-name_2.11/0.20.0"
      when(response.getResponseBody).thenReturn(
        Json
          .obj(
            "repo" -> repoName,
            "path" -> path,
            "children" -> Json.arr(
              Json.obj(
                "uri"    -> "/archived",
                "folder" -> true
              ),
              Json.obj(
                "uri"    -> "/someFile1.txt",
                "folder" -> false
              ),
              Json.obj(
                "uri"    -> "/someFile2.txt",
                "folder" -> false
              )
            )
          )
          .toString
      )

      Await.result(repo.fetchArtifactsPaths(artifactVersion), Duration.Inf) shouldBe Seq(
        s"$repoName$path/someFile1.txt",
        s"$repoName$path/someFile2.txt"
      )
    }

    "return an empty list of paths when there are no paths returned from Artifactory" in new Setup {
      when(response.getStatusCode).thenReturn(200)

      val repoName = "repo-name"
      val path     = "/uk/gov/hmrc/artifact-name_2.11/0.20.0"
      when(response.getResponseBody).thenReturn(
        Json
          .obj(
            "repo"     -> repoName,
            "path"     -> path,
            "children" -> Json.arr()
          )
          .toString
      )

      Await.result(repo.fetchArtifactsPaths(artifactVersion), Duration.Inf) shouldBe Seq.empty
    }

    "return an empty list if the artifact is not found" in new Setup {
      when(response.getStatusCode).thenReturn(404)

      Await.result(repo.fetchArtifactsPaths(artifactVersion), Duration.Inf) shouldBe Seq.empty
    }

    "throw an exception if the status code is not 200 or 404" in new Setup {
      when(response.getStatusCode).thenReturn(500)

      val url =
        s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifactVersion.path}"

      intercept[RuntimeException] {
        Await.result(repo.fetchArtifactsPaths(artifactVersion), Duration.Inf)
      }.getMessage shouldBe s"GET to $url returned with status code [500]"
    }
  }

  "distributeToBintray" should {

    "send a post request with proper authorization header and content type" in new Setup {

      when(response.getStatusCode).thenReturn(200)

      repo.distributeToBintray(Seq("some-path"))

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl                                    shouldBe s"https://${credentials.host}/artifactory/api/distribute"
      request.getMethod                                 shouldBe "POST"
      request.getHeaders.getFirstValue("Authorization") shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
      request.getHeaders.getFirstValue("content-type")  shouldBe "application/json"
    }

    "send a request with JSON body containing the target repo and the list of artifacts paths" in new Setup {

      when(response.getStatusCode).thenReturn(200)

      Await.result(repo.distributeToBintray(Seq("some-path1", "some-path2")), Duration.Inf) shouldBe
        "some-path1\nsome-path2\ndistributed to 'bintray-distribution' repository"

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      Json.parse(request.getStringData) shouldBe Json.obj(
        "targetRepo"        -> "bintray-distribution",
        "packagesRepoPaths" -> Json.arr("some-path1", "some-path2")
      )
    }

    "don't issue a request if the list of artifacts paths is empty" in new Setup {

      Await
        .result(repo.distributeToBintray(Seq.empty), Duration.Inf) shouldBe "Nothing distributed to 'bintray-distribution' repository"

      verifyZeroInteractions(httpClient)
    }

    "throw an exception if the status code is not 200" in new Setup {
      when(response.getStatusCode).thenReturn(500)

      val url = s"https://${credentials.host}/artifactory/api/distribute"

      intercept[RuntimeException] {
        Await.result(repo.distributeToBintray(Seq("some-path")), Duration.Inf)
      }.getMessage shouldBe s"POST to $url returned with status code [500]"
    }
  }

  private trait Setup {
    val credentials: DirectCredentials = Credentials(
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
    val repo           = new ArtifactoryConnector(httpClient, logger, credentials, repositoryName)
  }

}
