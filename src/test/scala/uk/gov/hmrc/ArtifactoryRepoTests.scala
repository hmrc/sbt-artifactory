package uk.gov.hmrc

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

class ArtifactoryRepoTests extends FlatSpec with Matchers with ScalaFutures {
  "artifactExists" should "return true if artifact exists" in pending

  it should "return false if the artifact doesn't exist" in pending

  "deleteSafe" should "return a successful future if it deletes the artifact" in pending

}
