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

import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers._
import scala.util.Random

class ArtifactDescriptionSpec extends FlatSpec with ScalaFutures {

  "path" should "be formed using pattern: 'org/name_scalaVersion/version'" in {
    ArtifactDescription("2.11", "org", "my-artifact", "0.1.0", Random.nextBoolean()).path shouldBe "org/my-artifact_2.11/0.1.0"
  }

  it should "be formed using pattern: 'org/name_scalaVersion/version' - case when org contains dots" in {
    ArtifactDescription("2.11", "uk.gov.hmrc", "my-artifact", "0.1.0", Random.nextBoolean()).path shouldBe "uk/gov/hmrc/my-artifact_2.11/0.1.0"
  }

  it should "be formed using pattern: 'org/name_scalaVersion/version' - case when artifact-name contains dots" in {
    ArtifactDescription("2.11", "uk.gov.hmrc", "my-artifact.public", "0.1.0", Random.nextBoolean()).path shouldBe "uk/gov/hmrc/my-artifact.public_2.11/0.1.0"
  }

  "toString" should "be formed using pattern: 'org.domain.name_scalaVersion:version'" in {
    ArtifactDescription("2.11", "org.domain", "my-artifact", "0.1.0", Random.nextBoolean()).toString shouldBe "org.domain.my-artifact_2.11:0.1.0"
  }

  "withCrossScalaVersion" should "create an ArtifactDescription" in {
    val publicArtifact = Random.nextBoolean()
    ArtifactDescription
      .withCrossScalaVersion("org.domain", "my-artifact", "0.1.0", "2.11", publicArtifact) shouldBe
      ArtifactDescription("2.11", "org.domain", "my-artifact", "0.1.0", publicArtifact)
  }
}
