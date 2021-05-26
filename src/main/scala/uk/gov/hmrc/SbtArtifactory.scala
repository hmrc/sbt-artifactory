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

import org.scalajs.sbtplugin.ScalaJSCrossVersion
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.isScalaJSProject
import sbt.Classpaths.publishTask
import sbt.Keys._
import sbt._
import uk.gov.hmrc.sbtsettingkeys.Keys.isPublicArtefact

import scala.concurrent.Await
import scala.concurrent.duration._

object SbtArtifactory extends sbt.AutoPlugin{

  private val distributionTimeout = 1.minute

  private val currentVersion = getClass.getPackage.getImplementationVersion

  private lazy val maybeArtifactoryUri        = sys.env.get("ARTIFACTORY_URI")
  private lazy val maybeArtifactoryUsername   = sys.env.get("ARTIFACTORY_USERNAME")
  private lazy val maybeArtifactoryPassword   = sys.env.get("ARTIFACTORY_PASSWORD")

  object autoImport {
    val repoKey             = settingKey[String]("Artifactory repository name")
    val artifactDescription = settingKey[ArtifactDescription]("Artifact description")
    val unpublish           = taskKey[Unit]("Unpublish from Artifactory")
  }

  import autoImport._

  private object EnvKeys {
    val publishPath = "PUBLISH_PATH"
  }

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle   := !sbtPlugin.value,
    licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    artifactDescription := ArtifactDescription.withCrossScalaVersion(
      org            = organization.value,
      name           = name.value,
      version        = version.value,
      scalaVersion   = scalaVersion.value,
      // sbtVersion needs to be resolved in the context of the pluginCrossBuild so it resolves correctly for cross-compiling sbt
      sbtVersion     = (sbtVersion in pluginCrossBuild).value,
      publicArtifact = isPublicArtefact.value,
      sbtPlugin      = sbtPlugin.value,
      scalaJsVersion = if (isScalaJSProject.value) Some(ScalaJSCrossVersion.currentBinaryVersion) else None
    ),
    repoKey := artifactoryRepoKey(sbtPlugin.value, isPublicArtefact.value),
    publishMavenStyle := !sbtPlugin.value,
    publishTo :=
      sys.env.get(EnvKeys.publishPath)
        .map(p => Resolver.file("file", file(p))(if (sbtPlugin.value) Resolver.ivyStylePatterns else Resolver.mavenStylePatterns))
        .orElse {
          maybeArtifactoryUri.map { uri =>
            if (sbtPlugin.value)
              Resolver.url(repoKey.value, url(s"$uri/${repoKey.value}"))(Resolver.ivyStylePatterns)
            else
              "Artifactory Realm" at s"$uri/${repoKey.value}"
          }
        },
    credentials ++= {
      // DirectCredentials.toString is not implemented for sbt 0.13
      val credString = maybeArtifactoryCredentials.map(cred => s"DirectCredentials(${cred.realm}, ${cred.host}, ${cred.userName}, ****)")
      sLog.value.info(message(name.value, s"Configuring Artifactory credentials: $credString"))
      maybeArtifactoryCredentials.toSeq
    },
    unpublish := {
      sLog.value.info(message(name.value, "Unpublishing from Artifactory..."))
      Await.result(
        artifactoryConnector(repoKey.value)
          .deleteVersion(artifactDescription.value, sLog.value),
        distributionTimeout
      )
    },
    publish := {
      sLog.value.info(message(name.value, "Publishing to Artifactory..."))
      // this deprecated function is required for sbt 0.13
      publishTask(publishConfiguration, deliver).value
    }
  )

  override def trigger = allRequirements

  private[hmrc] def artifactoryRepoKey(isSbtPlugin: Boolean, publicArtifact: Boolean): String =
    (isSbtPlugin, publicArtifact) match {
      case (false, false) => "hmrc-releases-local"
      case (false, true)  => "hmrc-public-releases-local"
      case (true, false)  => "hmrc-sbt-plugin-releases-local"
      case (true, true)   => "hmrc-public-sbt-plugin-releases-local"
    }

  private def maybeArtifactoryCredentials: Option[DirectCredentials] =
    for {
      uri      <- maybeArtifactoryUri
      username <- maybeArtifactoryUsername
      password <- maybeArtifactoryPassword
    } yield Credentials(
        realm    = "Artifactory Realm",
        host     = new URI(uri).getHost,
        userName = username,
        passwd   = password
      ).asInstanceOf[DirectCredentials]

  private def artifactoryConnector(repositoryName: String): ArtifactoryConnector =
    new ArtifactoryConnector(
      httpClient     = DispatchCrossSupport.http,
      credentials    = maybeArtifactoryCredentials.getOrElse(sys.error(s"SbtArtifactoryPlugin - No credential was found found")),
      repositoryName = repositoryName
    )

  private def message(projectName: String, msg: String): String =
    s"SbtArtifactoryPlugin [$currentVersion] ($projectName) - $msg"
}
