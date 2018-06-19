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

import java.util.Base64
import com.ning.http.client.Response
import dispatch.Defaults._
import dispatch._
import sbt.DirectCredentials
import scala.concurrent.Future

object Helpers {
  implicit class UrlDotPadding(val str: String) extends AnyVal {
    def dotsToSlashes: String = str.replaceAll("""\.""", "/")
  }
}

case class ArtifactVersion(
  scalaVersion: String,
  org: String,
  name: String,
  version: String
) {

  import Helpers._
  def path: String = s"${org.dotsToSlashes}/${name.dotsToSlashes}_$scalaVersion/$version"
}


abstract class ArtifactoryRepo(
  credentials: DirectCredentials,
  repoKey: String
) {

  def execute(request: Req): Future[Response] =
    Http(request)

  def log(msg: String): Unit

  val encodedCredentials: String = Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())
  val authHeader = s"Basic $encodedCredentials"

  log(s"Using HTTP Basic Authorization with header $authHeader")

  implicit class ReqAuthVerb(req: Req) {
    def withAuth: Req = req <:< Seq("Authorization" -> authHeader)
  }

  def artifactExists(artifact: ArtifactVersion): Future[Boolean] = {

    val artifactLocation = artifactUrl(artifact)
    log(s"Calling API URL $artifactLocation")
    Console.println(s"Using remote API url $artifactLocation")

    execute(url(artifactLocation).GET.withAuth) map { resp =>
      log(s"Received status code ${resp.getStatusCode} when checking if artifact $artifact exists")
      resp.getStatusCode match {
        // If the artifact is found, the Artifactory API returns 200, and an HTML page with a list of available files and versions for the artifact.
        case 200 => {
          log(s"Artifact $artifact exists")
          true
        }
        // If not, we usually get 404, but in here we treat all non 200 response codes the same way.
        case _ => {
          log(s"Artifact $artifact not found on the remote repository ${credentials.host}/$repoKey")
          false
        }
      }
    }
  }

  private def artifactUrl(artifact: ArtifactVersion) = {
    s"https://${credentials.host}/artifactory/$repoKey/${artifact.path}/"
  }

  def deleteSafe(artifact: ArtifactVersion): Future[Response] = {
    artifactExists(artifact) flatMap {
      case true => deleteVersion(artifact)
      case false => {
        val err = new RuntimeException(s"Artifact $artifact does not exist.")
        log(err.getMessage)
        Future.failed(err)
      }
    }
  }

  def deleteVersion(artifact: ArtifactVersion): Future[Response] = {
    val artifactLocation = artifactUrl(artifact)
    log(s"Calling DELETE $artifactLocation")

    execute(url(artifactLocation).DELETE.withAuth) flatMap { resp =>
      log(s"Received status code ${resp.getStatusCode} when deleting artifact")
      resp.getStatusCode match {
        case 200 | 204 => Future.successful(resp)
        case _ => {
          val err = new RuntimeException(s"Expected status code to be 200 or 204, got ${resp.getStatusCode} instead. Status text: ${resp.getStatusText}; Response body: ${resp.getResponseBody}")
          log(err.getMessage)
          Future.failed(err)
        }
      }
    }
  }
}
