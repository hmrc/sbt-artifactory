/*
 * Copyright 2021 HM Revenue & Customs
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

import dispatch.{Http, Req}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => is}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import sbt.{Credentials, DirectCredentials, MultiLogger}

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ArtifactoryConnectorSpec extends AnyWordSpec with MockitoSugar with ScalaFutures {

  "deleteVersion" should {

    "send a delete request with proper authorization header" in new Setup {

      when(response.getStatusCode).thenReturn(204)

      repo.deleteVersion(artifact, new MultiLogger(List.empty))

      val reqCaptor = ArgumentCaptor.forClass(classOf[Req])
      verify(httpClient).apply(reqCaptor.capture())(is(executionContext))

      val request = reqCaptor.getValue.toRequest
      request.getUrl                                    shouldBe s"https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/"
      request.getMethod                                 shouldBe "DELETE"
      DispatchCrossSupport.extractRequestHeader(request, "Authorization") shouldBe "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
    }

    "return a failure when the delete API call returns an unexpected result" in new Setup {

      when(response.getStatusCode).thenReturn(401)

      ScalaFutures.whenReady(repo.deleteVersion(artifact, new MultiLogger(List.empty)).failed) { exception =>
        exception.getMessage shouldBe s"Artifact '$artifact' could not be deleted from https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/. Received status 401"
      }

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

    val response = mock[DispatchCrossSupport.Response]
    when(httpClient(any[Req])(is(executionContext))).thenReturn(Future.successful(response))

    val repositoryName = "hmrc-releases-local"
    val repo           = new ArtifactoryConnector(httpClient, credentials, repositoryName)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds)
}
