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

import sbt.CrossVersion

case class ArtifactDescription(
  scalaVersion: String,
  org: String,
  name: String,
  version: String,
  publicArtifact: Boolean
) {

  lazy val path: String = s"${org.dotsToSlashes}/${name}_$scalaVersion/$version"

  override lazy val toString: String = s"$org.${name}_$scalaVersion:$version"

  private implicit class UrlDotPadding(val str: String) {
    def dotsToSlashes: String = str.replaceAll("""\.""", "/")
  }
}

object ArtifactDescription {

  def withCrossScalaVersion(
    org: String,
    name: String,
    version: String,
    scalaVersion: String,
    publicArtifact: Boolean): ArtifactDescription = {
    val crossScalaVersion = CrossVersion.partialVersion(scalaVersion) match {
      case Some((major, minor)) => s"$major.$minor"
      case _                    => throw new Exception(s"Unable to extract Scala version from $scalaVersion")
    }

    ArtifactDescription(
      crossScalaVersion,
      org,
      name,
      version,
      publicArtifact
    )
  }
}
