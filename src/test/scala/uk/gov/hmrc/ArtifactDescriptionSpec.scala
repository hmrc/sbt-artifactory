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

import org.scalatest.Matchers._
import org.scalatest.WordSpec

import scala.util.Random

class ArtifactDescriptionSpec extends WordSpec {

  "withCrossScalaVersion" should {

    val publicArtifact = Random.nextBoolean()

    "create a MavenArtifactDescription if it's not a plugin" in {
      ArtifactDescription
        .withCrossScalaVersion(
          org            = "org.domain",
          name           = "my-artifact",
          version        = "0.1.0",
          scalaVersion   = "2.11",
          sbtVersion     = "0.13.17",
          publicArtifact = publicArtifact,
          sbtPlugin      = false,
          scalaJsVersion = Some("0.6.26")
        ) shouldBe
        MavenArtifactDescription(
          org            = "org.domain",
          name           = "my-artifact",
          version        = "0.1.0",
          scalaVersion   = "2.11",
          publicArtifact = publicArtifact,
          scalaJsVersion = Some("0.6.26")
        )
    }

    "create an IvySbtArtifactDescription if it's a plugin" in {
      ArtifactDescription
        .withCrossScalaVersion(
          org            = "org.domain",
          name           = "my-artifact",
          version        = "0.1.0",
          scalaVersion   = "2.11",
          sbtVersion     = "0.13.17",
          publicArtifact = publicArtifact,
          sbtPlugin      = true,
          None
        ) shouldBe
        IvySbtArtifactDescription(
          org            = "org.domain",
          name           = "my-artifact",
          version        = "0.1.0",
          scalaVersion   = "2.11",
          sbtVersion     = "0.13.17",
          publicArtifact = publicArtifact
        )
    }
  }

  "MavenArtifactDescription.path" should {

    "be formed using pattern: 'org/name_scalaVersion/version'" in {
      MavenArtifactDescription("org", "my-artifact", "0.1.0", "2.11", Random.nextBoolean(), None).path shouldBe "org/my-artifact_2.11/0.1.0"
    }

    "be formed using pattern: 'org/name_scalaVersion/version' - case when org contains dots" in {
      MavenArtifactDescription("uk.gov.hmrc", "my-artifact", "0.1.0", "2.11", Random.nextBoolean(), None).path shouldBe "uk/gov/hmrc/my-artifact_2.11/0.1.0"
    }

    "be formed using pattern: 'org/name_scalaVersion/version' - case when artifact-name contains dots" in {
      MavenArtifactDescription("uk.gov.hmrc", "my-artifact.public", "0.1.0", "2.11", Random.nextBoolean(), None).path shouldBe "uk/gov/hmrc/my-artifact.public_2.11/0.1.0"
    }

    "should convert the name of the artifact to lowercase" in {
      MavenArtifactDescription("org", "My-Artifact", "0.1.0", "2.11", Random.nextBoolean(), None).path shouldBe "org/my-artifact_2.11/0.1.0"
    }

    "be formed using pattern: 'org/name_sjs<scalaJSVersion>_scalaVersion/version'" in {
      MavenArtifactDescription("org", "my-artifact", "0.1.0", "2.11", Random.nextBoolean(), Some("0.6.26")).path shouldBe "org/my-artifact_sjs0.6.26_2.11/0.1.0"
    }
  }

  "MavenArtifactDescription.toString" should {
    "be formed using pattern: 'org.domain.name_scalaVersion:version'" in {
      MavenArtifactDescription("org.domain", "my-artifact", "0.1.0", "2.11", Random.nextBoolean(), None).toString shouldBe "org.domain:my-artifact:scala_2.11:0.1.0"
    }

    "be formed using pattern: 'org.domain.name_sjs<scalaJSVersion>_scalaVersion:version'" in {
      MavenArtifactDescription("org.domain", "my-artifact", "0.1.0", "2.11", Random.nextBoolean(), Some("0.6.26")).toString shouldBe "org.domain:my-artifact_sjs0.6.26:scala_2.11:0.1.0"
    }
  }

  "IvySbtArtifactDescription.path" should {

    "be formed using pattern: 'org/name/scala_scalaVersion/sbt_sbtVersion/version'" in {
      IvySbtArtifactDescription("org", "my-artifact", "0.1.0", "2.11", "0.13.17", Random.nextBoolean()).path shouldBe "org/my-artifact/scala_2.11/sbt_0.13/0.1.0"
    }

    "be formed using pattern: 'org/name_scalaVersion/version' - case when org contains dots" in {
      IvySbtArtifactDescription("uk.gov.hmrc", "my-artifact", "0.1.0", "2.11", "0.13.17", Random.nextBoolean()).path shouldBe "uk.gov.hmrc/my-artifact/scala_2.11/sbt_0.13/0.1.0"
    }

    "be formed using pattern: 'org/name_scalaVersion/version' - case when artifact-name contains dots" in {
      IvySbtArtifactDescription("uk.gov.hmrc", "my-artifact.public", "0.1.0", "2.11", "0.13.17", Random.nextBoolean()).path shouldBe "uk.gov.hmrc/my-artifact.public/scala_2.11/sbt_0.13/0.1.0"
    }

    "should convert the name of the artifact to lowercase" in {
      IvySbtArtifactDescription("org", "My-Artifact", "0.1.0", "2.11", "0.13.17", Random.nextBoolean()).path shouldBe "org/my-artifact/scala_2.11/sbt_0.13/0.1.0"
    }
  }

  "IvySbtArtifactDescription.toString" should {
    "be formed using pattern: 'org.domain.name/scala_scalaVersion/sbt_sbtVersion:version'" in {
      IvySbtArtifactDescription("uk.gov.hmrc", "my-artifact", "0.1.0", "2.11", "0.13.17", Random.nextBoolean()).toString shouldBe "uk.gov.hmrc:my-artifact:scala_2.11:sbt_0.13:0.1.0"
    }
  }

}
