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

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

class SbtArtifactorySpec extends WordSpec with Matchers with TableDrivenPropertyChecks {

  "artifactoryRepoKey" should {

    val scenarios = Table(
      ("sbtPlugin", "publicArtifact", "expectedRepoKey"),
      (false, false, "hmrc-releases-local"),
      (false, true, "hmrc-public-releases-local"),
      (true, false, "hmrc-sbt-plugin-releases-local"),
      (true, true, "hmrc-public-sbt-plugin-releases-local")
    )

    forAll(scenarios) { (sbtPlugin, publicArtifact, expectedRepoKey) =>
      s"return '$expectedRepoKey' repository key when sbtPlugin=$sbtPlugin and publicArtifact=$publicArtifact" in {
        SbtArtifactory.artifactoryRepoKey(sbtPlugin, publicArtifact) shouldBe expectedRepoKey
      }
    }
  }
}
