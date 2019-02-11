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

import java.util.Base64

import com.ning.http.client.Response
import dispatch.Defaults._
import dispatch._
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import sbt.DirectCredentials

class ArtifactoryConnector(httpClient: Http, credentials: DirectCredentials, repositoryName: String) {
  private val targetRepository = "bintray-distribution"

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

  def fetchArtifactsPaths(artifact: ArtifactDescription): Future[Set[String]] = {

    import ArtifactsPathsModel._

    def collectArtifacts(currentPaths: ArtifactsPaths, fetchUrl: String, collectedPaths: Set[String]) = {
      val ArtifactsPaths(repo, path, childrenPaths) = currentPaths
      childrenPaths.foldLeft(Future.successful(collectedPaths)) {
        case (futureCollectedPaths, Folder(folderUri)) =>
          for {
            collectedPaths <- futureCollectedPaths
            newPaths       <- fetchAndCollect(s"$fetchUrl$folderUri", collectedPaths)
          } yield collectedPaths ++ newPaths
        case (futureCollectedPaths, Artifact(artifactUri)) =>
          futureCollectedPaths map (_ + s"$repo$path$artifactUri")
      }
    }

    def fetchAndCollect(fetchUrl: String, collectedPaths: Set[String] = Set.empty): Future[Set[String]] =
      httpClient(url(fetchUrl).GET) flatMap { response =>
        response.getStatusCode match {
          case 404 =>
            Future.successful(collectedPaths)
          case 200 =>
            val currentPaths = Json.parse(response.getResponseBody()).as[ArtifactsPaths]
            collectArtifacts(currentPaths, fetchUrl, collectedPaths)
          case statusCode =>
            throw new RuntimeException(
              s"GET to $fetchUrl returned with status code [$statusCode]${extractErrorMessage(response)}"
            )
        }
      }

    fetchAndCollect(
      s"https://${credentials.host}/artifactory/api/storage/$repositoryName/${artifact.path}"
    )
  }

  def distributeToBintray(artifactsPaths: Set[String]): Future[String] = artifactsPaths match {
    case paths if paths.isEmpty =>
      Future.successful(s"Nothing distributed to '$targetRepository' repository")
    case paths =>
      val payload = Json.obj(
        "targetRepo"        -> targetRepository,
        "packagesRepoPaths" -> Json.arr(paths.map(toJsFieldJsValueWrapper(_)).toList: _*)
      )

      val distributeUrl = s"https://${credentials.host}/artifactory/api/distribute"

      val request = url(distributeUrl).POST
        .setBody(payload.toString())
        .setHeader("content-type", "application/json")
        .withAuth

      httpClient(request) map { response =>
        response.getStatusCode match {
          case 200 =>
            s"Artifacts distributed to '$targetRepository' repository"
          case statusCode =>
            throw new RuntimeException(
              s"POST to $distributeUrl returned with status code [$statusCode]${extractErrorMessage(response)}"
            )
        }
      }
  }

  private def extractErrorMessage(response: Response): String =
    (Json.parse(response.getResponseBody()) \ "message")
      .asOpt[String]
      .map(m => s" and message: $m")
      .getOrElse("")

  private object ArtifactsPathsModel {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json._

    case class ArtifactsPaths(repoKey: String, path: String, childrenPaths: Set[ArtifactPath])

    sealed trait ArtifactPath {
      val uri: String
    }
    case class Artifact(uri: String) extends ArtifactPath
    case class Folder(uri: String) extends ArtifactPath

    private implicit val artifactPathReads: Reads[ArtifactPath] = (
      (__ \ "uri").read[String] and
        (__ \ "folder").read[Boolean]
    )(
      (uri: String, folder: Boolean) =>
        if (folder) Folder(uri)
        else Artifact(uri)
    )

    implicit val artifactsPathsReads: Reads[ArtifactsPaths] = (
      (__ \ "repo").read[String] and
        (__ \ "path").read[String] and
        (__ \ "children").read[Set[ArtifactPath]]
    )(ArtifactsPaths.apply _)
  }

  private implicit class ReqAuthVerb(req: Req) {
    val encodedCredentials: String =
      Base64.getEncoder.encodeToString(s"${credentials.userName}:${credentials.passwd}".getBytes())

    def withAuth: Req = req <:< Seq("Authorization" -> s"Basic $encodedCredentials")
  }
}
