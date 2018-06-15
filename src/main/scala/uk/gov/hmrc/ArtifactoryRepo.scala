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
import dispatch.Defaults._
import dispatch._
import sbt.{DirectCredentials, Logger, ProcessLogger}
import scala.concurrent.Future

object Helpers {
  implicit class UrlDotPadding(val str: String) extends AnyVal {
    def dotsToSlashes: String = str.replaceAll("""\.""", "/")
  }
}

case class ArtifactoryVersion(
  scalaVersion: String,
  org: String,
  name: String,
  version: String
) {

  import Helpers._
  def apiUrl: String = s"${org.dotsToSlashes}/${name.dotsToSlashes}_$scalaVersion/$version"
}


case class ArtifactoryRepo(
  logger: Logger,
  credentials: DirectCredentials,
  repoKey: String,
  artifactoryUrl: String
) {


  val encodedCredentials: Array[Byte] = Base64.getEncoder.encode(s"${credentials.userName}:${credentials.passwd}".getBytes())
  val authHeader = s"Basic: $encodedCredentials"

  logger.info(s"Using HTTP Basic Authorization with header $authHeader")

  implicit class ReqAuthVerb(req: Req) {
    def withAuth: Req = req <:< Seq("Authorization" -> authHeader)
  }

  def artifactExists(artifact: ArtifactoryVersion): Future[Boolean] = {

    val apiUrl = s"$artifactoryUrl/$repoKey/${artifact.apiUrl}/"
    logger.info(s"Calling API URL $apiUrl")
    Console.println(s"Using remote API url $apiUrl")

    Http(url(apiUrl).withAuth) map { resp =>
      logger.info(s"Received status code ${resp.getStatusCode} when checking if artifact $artifact exists")
      resp.getStatusCode match {
        // If the artifact is found, the Artifactory API returns 200, and an HTML page with a list of avaialable files and versions for the artifact.
        case 200 => {
          logger.info(s"Artifact $artifact exists")
          true
        }

        // If not, we usually get 404, but in here we treat all non 200 response codes the same way.
        case _ => {
          logger.info(s"Artifact $artifact not found on the remote repository $artifactoryUrl/$repoKey")
          false
        }
      }
    }
  }

  def deleteSafe(artifact: ArtifactoryVersion): Future[dispatch.Res] = {
    artifactExists(artifact) flatMap {
      case true => deleteVersion(artifact)
      case false => {
        val err = new RuntimeException(s"Artifact $artifact does not exist.")
        logger.trace(err)
        Future.failed(err)
      }
    }
  }

  def deleteVersion(artifact: ArtifactoryVersion): Future[Res] = {
    val apiUrl = s"$artifactoryUrl/$repoKey/${artifact.apiUrl}/"
    logger.info(s"Calling DELETE $apiUrl")

    Http(url(apiUrl).DELETE.withAuth) map { resp =>
      logger.info(s"Received status code ${resp.getStatusCode} when deleting artifact")
      resp.getStatusCode match {
        case 200 | 204 => resp
        case _ => {
          val err = new RuntimeException(s"Expected status code to be 200 or 204, got ${resp.getStatusCode} instead. Status text: ${resp.getStatusText}; Response body: ${resp.getResponseBody}")
          logger.trace(err)
          throw err
        }
      }
    }
  }
}
