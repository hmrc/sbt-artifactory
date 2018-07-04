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
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{Json, Reads}
import sbt.DirectCredentials

class ArtifactoryConnector(httpClient: Http, credentials: DirectCredentials, repositoryName: String) {

  private val targetRepository = "bintray-distribution"
  private val encodedCredentials: String =
    Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())
  private val authHeader = s"Basic $encodedCredentials"

  def deleteVersion(artifact: ArtifactDescription): Future[String] = {
    val artifactUrl = s"https://${credentials.host}/artifactory/$repositoryName/${artifact.path}/"

    httpClient(url(artifactUrl).DELETE.withAuth)
      .map(_.getStatusCode)
      .map {
        case 200 | 204 =>
          s"Artifact '$artifact' deleted successfully from $artifactUrl"
        case 404 =>
          s"Artifact '$artifact' not found on $artifactUrl. No action taken."
        case status =>
          throw new RuntimeException(
            s"Artifact '$artifact' could not be deleted from $artifactUrl. Received status $status"
          )
      }
  }

  def fetchArtifactsPaths(artifact: ArtifactDescription): Future[Seq[String]] = {
    val getFileListUrl =
      s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifact.path}"

    httpClient(url(getFileListUrl).GET) map { resp =>
      resp.getStatusCode match {
        case 404 => Seq.empty
        case 200 =>
          Json.parse(resp.getResponseBody()).as[(String, String, Seq[String])] match {
            case (repo, path, artifactPaths) => artifactPaths.map(artifactPath => s"$repo$path$artifactPath")
          }
        case statusCode => throw new RuntimeException(s"GET to $getFileListUrl returned with status code [$statusCode]")
      }
    }
  }

  def distributeToBintray(artifactsPaths: Seq[String]): Future[String] = artifactsPaths match {
    case Nil =>
      Future.successful(s"Nothing distributed to '$targetRepository' repository")
    case paths =>
      val payload = Json.obj(
        "targetRepo"        -> targetRepository,
        "packagesRepoPaths" -> Json.arr(paths.map(toJsFieldJsValueWrapper(_)): _*)
      )

      val distributeUrl = s"https://${credentials.host}/artifactory/api/distribute"

      val request = url(distributeUrl).POST
        .setBody(payload.toString())
        .setHeader("content-type", "application/json")
        .withAuth

      httpClient(request) map { response =>
        response.getStatusCode match {
          case 200 =>
            s"${artifactsPaths.mkString("", "\n", "\n")}distributed to '$targetRepository' repository"
          case statusCode =>
            val errorMessagePart = (Json.parse(response.getResponseBody()) \ "message")
              .asOpt[String]
              .map(m => s" and message: $m")
              .getOrElse("")

            throw new RuntimeException(
              s"POST to $distributeUrl returned with status code [$statusCode]$errorMessagePart"
            )
        }
      }
  }

  private case class ArtifactPath(uri: String, folder: Boolean)
  implicit private val artifactPathReads: Reads[ArtifactPath] = Json.reads[ArtifactPath]

  implicit private val artifactsPathsReads: Reads[(String, String, Seq[String])] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json._

    (
      (__ \ "repo").read[String] and
        (__ \ "path").read[String] and
        (__ \ "children").read[Seq[ArtifactPath]].map(_.filterNot(_.folder).map(_.uri))
    ).tupled
  }

  private implicit class ReqAuthVerb(req: Req) {
    def withAuth: Req = req <:< Seq("Authorization" -> authHeader)
  }
}
