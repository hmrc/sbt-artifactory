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

import java.net.URI
import java.util.Base64
import sbt.{Credentials, DirectCredentials}
import scala.concurrent.Future
import dispatch._
import dispatch.Defaults._

object Helpers {
  implicit class UrlDotPadding(val str: String) extends AnyVal {
    def dotsToSlashes: String = str.replaceAll("""\.""", "/")
  }
}

case class Artifact(
  scalaVersion: String,
  org: String,
  name: String,
  version: String
) {

  import Helpers._
  def apiUrl: String = s"${org.dotsToSlashes}/${name.dotsToSlashes}_$scalaVersion/$version"
}


case class ArtifactoryRepo(
  credentials: DirectCredentials,
  repoKey: String,
  artifactoryUrl: String
) {


  val encodedCredentials = Base64.getEncoder.encode(s"${credentials.userName}:${credentials.passwd}".getBytes())
  val authHeader = s"Basic: $encodedCredentials"

  implicit class ReqAuthVerb(req: Req) {
    def withAuth: Req = req <:< Seq("Authorization" -> authHeader)
  }

  def artifactExists(artifact: Artifact): Future[Boolean] = {

    Http(url(s"$artifactoryUrl/$repoKey/${artifact.apiUrl}") withAuth) map { resp =>
      resp.getStatusCode match {
        // If the artifact is found, the Artifactory API returns 200, and an HTML page with a list of avaialable files and versions for the artifact.
        case 200 => true

        // If not, we usually get 404, but in here we treat all non 200 response codes the same way.
        case _ => false
      }
    }
  }

  def deleteSafe(artifact: Artifact): Future[dispatch.Res] = {
    artifactExists(artifact) flatMap {
      case true => deleteVersion(artifact)
      case false => Future.failed(new RuntimeException(s"Artifact $artifact does not exist."))
    }
  }

  def deleteVersion(artifact: Artifact): Future[Res] = {
    Http(url(s"$artifactoryUrl/$repoKey/${artifact.apiUrl}").DELETE withAuth) map { resp =>
      resp.getStatusCode match {
        case 200 | 204 => resp
        case _ => throw new RuntimeException(s"Expected status code to be 200 or 204, got ${resp.getStatusCode} instead. Status text: ${resp.getStatusText}; Response body: ${resp.getResponseBody}")
      }
    }
  }
}
