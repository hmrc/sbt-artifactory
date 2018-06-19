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
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

import scala.concurrent.duration._

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

abstract class ArtifactoryRepo(credentials: DirectCredentials, repoKey: String) {

  def execute(request: Req): Future[Response] =
    Http(request)

  def log(msg: String): Unit

  val encodedCredentials: String =
    Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())
  val authHeader = s"Basic $encodedCredentials"

  implicit class ReqAuthVerb(req: Req) {
    def withAuth: Req = req <:< Seq("Authorization" -> authHeader)
  }

  def deleteVersion(artifact: ArtifactVersion): Try[String] = {
    val artifactUrl = s"https://${credentials.host}/artifactory/$repoKey/${artifact.path}/"
    log(s"Calling DELETE $artifactUrl")

    val futureResponse = execute(url(artifactUrl).DELETE.withAuth) map { resp =>
      resp.getStatusCode match {
        case 200 | 204 => Success(s"Artifact '${artifact.path}' deleted successfully from ${credentials.host}")
        case 404       => Success(s"Artifact '${artifact.path}' not found on ${credentials.host}. No action taken.")
        case status => {
          val err = new RuntimeException(
            s"Artifact '${artifact.path}' could not be deleted from ${credentials.host}. Received status $status")
          log(s"API call to $artifactUrl returned $status. Response body: \n${resp.getResponseBody}")
          Failure(err)
        }
      }
    }

    Await.result(futureResponse, 15.seconds)
  }
}
