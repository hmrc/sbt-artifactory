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

import sbt.Keys._
import sbt._

object SbtArtifactory extends sbt.AutoPlugin {

  object autoImport { } // required for AutoPlugins, even if empty

  private object EnvKeys {
    val publishPath = "PUBLISH_PATH"
  }

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := !sbtPlugin.value,
    publishTo :=
      sys.env.get(EnvKeys.publishPath)
        .map(p => Resolver.file("file", file(p))(if (sbtPlugin.value) Resolver.ivyStylePatterns else Resolver.mavenStylePatterns))
  )

  override def trigger = allRequirements
}
