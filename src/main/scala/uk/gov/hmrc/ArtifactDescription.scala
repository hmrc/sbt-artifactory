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

import sbt.CrossVersion

sealed trait ArtifactDescription {
  val org: String
  val name: String
  val version: String
  val scalaVersion: String
  val publicArtifact: Boolean

  def path: String
}

object ArtifactDescription {

  def withCrossScalaVersion(
    org: String,
    name: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String,
    publicArtifact: Boolean,
    sbtPlugin: Boolean,
    scalaJsVersion: Option[String]): ArtifactDescription = {
    val crossScalaVersion = CrossVersion.partialVersion(scalaVersion) match {
      case Some((major, minor)) => s"$major.$minor"
      case _                    => throw new Exception(s"Unable to extract Scala version from $scalaVersion")
    }

    if (sbtPlugin)
      IvySbtArtifactDescription(
        org,
        name,
        version,
        crossScalaVersion,
        sbtVersion,
        publicArtifact
      )
    else
      MavenArtifactDescription(
        org,
        name,
        version,
        crossScalaVersion,
        publicArtifact,
        scalaJsVersion
      )
  }
}

case class MavenArtifactDescription(
  org: String,
  name: String,
  version: String,
  scalaVersion: String,
  publicArtifact: Boolean,
  scalaJsVersion: Option[String]
) extends ArtifactDescription {

  private lazy val scalaJsVersionPrefix = scalaJsVersion.fold("")("_sjs" + _)

  override lazy val toString: String = s"$org:$name$scalaJsVersionPrefix:scala_$scalaVersion:$version"

  override lazy val path: String =
    s"${dotsToSlashes(org)}/${name.toLowerCase()}${scalaJsVersionPrefix}_$scalaVersion/$version"

  private def dotsToSlashes(expression: String): String = expression.replaceAll("""\.""", "/")
}

case class IvySbtArtifactDescription(
  org: String,
  name: String,
  version: String,
  scalaVersion: String,
  sbtVersion: String,
  publicArtifact: Boolean
) extends ArtifactDescription {

  override lazy val toString: String = s"$org:$name:scala_$scalaVersion:sbt_$sbtVersionFragment:$version"

  private lazy val sbtVersionFragment =
    CrossVersion
      .sbtApiVersion(sbtVersion)
      .map {
        case (major, minor) => s"$major.$minor"
      }
      .getOrElse {
        throw new Exception(s"Unable to extract Sbt version from $sbtVersion")
      }

  override lazy val path: String = s"$org/${name.toLowerCase()}/scala_$scalaVersion/sbt_$sbtVersionFragment/$version"

}
