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

import com.ning.http.client.Response
import dispatch.{Http, Req}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => is}
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import sbt.{Credentials, DirectCredentials}
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class ArtifactoryConnectorSpec extends WordSpec with MockitoSugar {

  "deleteVersion" should {

    "send a delete request with proper authorization header" in new Setup {

      when(response.getStatusCode).thenReturn(204)

      repo.deleteVersion(artifact)

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl                                    shouldBe s"https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/"
      request.getMethod                                 shouldBe "DELETE"
      request.getHeaders.getFirstValue("Authorization") shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
    }

    "return successfully if it deletes the artifact" in new Setup {

      when(response.getStatusCode).thenReturn(204)

      repo.deleteVersion(artifact).awaitResult shouldBe
        s"Artifact '$artifact' deleted successfully from https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/"
    }

    "return successfully with a proper message if the artifact doesn't exist" in new Setup {

      when(response.getStatusCode).thenReturn(404)

      repo.deleteVersion(artifact).awaitResult shouldBe
        s"Artifact '$artifact' not found on https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/. No action taken."
    }

    "return a failure when the delete API call returns an unexpected result" in new Setup {

      when(response.getStatusCode).thenReturn(401)

      intercept[RuntimeException] {
        repo.deleteVersion(artifact).awaitResult
      }.getMessage shouldBe s"Artifact '$artifact' could not be deleted from https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/. Received status 401"
    }
  }

  "fetchArtifactsPaths" should {
    "send a get request to fetch the list of paths to artifacts" in new Setup {
      when(response.getStatusCode).thenReturn(200)

      repo.fetchArtifactsPaths(artifact)

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl    shouldBe s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifact.path}"
      request.getMethod shouldBe "GET"
    }

    "return a list of paths to artifacts fetched from Artifactory" in new Setup {
      when(response.getStatusCode).thenReturn(200)

      val repoName = "repo-name"
      val path     = "/uk/gov/hmrc/artifact-name_2.11/0.20.0"
      when(response.getResponseBody)
        .thenReturn(
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
        .thenReturn(
          Json
            .obj(
              "repo" -> repoName,
              "path" -> s"$path/archived",
              "children" -> Json.arr(
                Json.obj(
                  "uri"    -> "/docs",
                  "folder" -> true
                ),
                Json.obj(
                  "uri"    -> "/archivedFile.txt",
                  "folder" -> false
                )
              )
            )
            .toString
        )
        .thenReturn(
          Json
            .obj(
              "repo" -> repoName,
              "path" -> s"$path/archived/docs",
              "children" -> Json.arr(
                Json.obj(
                  "uri"    -> "/archivedDocsFile.txt",
                  "folder" -> false
                )
              )
            )
            .toString
        )

      repo.fetchArtifactsPaths(artifact).awaitResult shouldBe Set(
        s"$repoName$path/someFile1.txt",
        s"$repoName$path/someFile2.txt",
        s"$repoName$path/archived/archivedFile.txt",
        s"$repoName$path/archived/docs/archivedDocsFile.txt"
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

      repo.fetchArtifactsPaths(artifact).awaitResult shouldBe Set.empty
    }

    "return an empty list if the artifact is not found" in new Setup {
      when(response.getStatusCode).thenReturn(404)

      repo.fetchArtifactsPaths(artifact).awaitResult shouldBe Set.empty
    }

    "throw an exception if the status code is not 200 or 404" in new Setup {
      when(response.getStatusCode).thenReturn(500)
      when(response.getResponseBody).thenReturn(Json.obj("message" -> "error").toString())

      val url =
        s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifact.path}"

      intercept[RuntimeException] {
        repo.fetchArtifactsPaths(artifact).awaitResult
      }.getMessage shouldBe s"GET to $url returned with status code [500] and message: error"
    }
  }

  "distributeToBintray" should {

    "send a post request with proper authorization header and content type" in new Setup {

      when(response.getStatusCode).thenReturn(200)

      repo.distributeToBintray(Set("some-path"))

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

      repo.distributeToBintray(Set("some-path1", "some-path2")).awaitResult shouldBe
        "Artifacts distributed to 'bintray-distribution' repository"

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      Json.parse(request.getStringData) shouldBe Json.obj(
        "targetRepo"        -> "bintray-distribution",
        "packagesRepoPaths" -> Json.arr("some-path1", "some-path2")
      )
    }

    "don't issue a request if the list of artifacts paths is empty" in new Setup {

      repo
        .distributeToBintray(Set.empty)
        .awaitResult shouldBe "Nothing distributed to 'bintray-distribution' repository"

      verifyZeroInteractions(httpClient)
    }

    "throw an exception with an error message if the status code is not 200" in new Setup {
      when(response.getStatusCode).thenReturn(500)

      val message = "The following artifacts could not be distributed: artifact 1, artifact 2"

      when(response.getResponseBody).thenReturn(
        Json
          .obj(
            "message" -> message
          )
          .toString
      )

      val url = s"https://${credentials.host}/artifactory/api/distribute"

      intercept[RuntimeException] {
        repo.distributeToBintray(Set("some-path")).awaitResult
      }.getMessage shouldBe s"POST to $url returned with status code [500] and message: $message"
    }
  }

  private trait Setup {

    val credentials: DirectCredentials = Credentials(
      realm    = "Artifactory Realm",
      host     = "localhost",
      userName = "username",
      passwd   = "password"
    ).asInstanceOf[DirectCredentials]

    val artifact = ArtifactDescription.withCrossScalaVersion(
      org            = "uk.gov.hmrc",
      name           = "my-artifact",
      version        = "0.1.0",
      scalaVersion   = "2.11",
      sbtVersion     = "0.13.17",
      publicArtifact = Random.nextBoolean(),
      sbtPlugin      = Random.nextBoolean(),
      scalaJsVersion = Some("0.6.26")
    )

    val httpClient = mock[Http]

    val response = mock[Response]
    when(httpClient(any[Req])(is(executionContext))).thenReturn(Future.successful(response))

    val repositoryName = "hmrc-releases-local"
    val repo           = new ArtifactoryConnector(httpClient, credentials, repositoryName)
  }

  private implicit class FutureOps[T](future: Future[T]) {
    lazy val awaitResult: T = Await.result(future, 10 seconds)
  }

}
