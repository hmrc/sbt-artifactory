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

import sbt.Keys._
import sbt._

object SbtArtifactory extends sbt.AutoPlugin {

  val uri      = sys.env.getOrElse("ARTIFACTORY_URI", "")
  val host     = new URI(uri).getHost
  val username = sys.env.getOrElse("ARTIFACTORY_USERNAME", "")
  val password = sys.env.getOrElse("ARTIFACTORY_PASSWORD", "")

  object autoImport {
    val artifactoryUnpublish = taskKey[Unit]("artifactory_unpublish")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    publishTo := { if (uri.isEmpty) None else Some("Artifactory Realm" at uri + "/hmrc-releases") },
    credentials += Credentials("Artifactory Realm", host, username, password),
    artifactoryUnpublish := Def.task {


      postPublishCleanup(
        credentials.value.head,

      )

    }
  )


  def postPublishCleanup(
    credentials: Credentials,

  ) = {
    val sbtArtifactoryRepo = ArtifactoryRepo(
      credentials,
      "hmrc-releases-local",
      uri
    )
  }

}
