
# sbt-artifactory

 [ ![Download](https://img.shields.io/github/v/release/hmrc/sbt-artifactory) ](https://open.artefacts.tax.service.gov.uk/ivy2/uk.gov.hmrc/sbt-artifactory/scala_2.12/sbt_1.0/)

SbtArtifactory is no longer needed in projects, it is only for use on the build servers.

### 2.x.x

The plugin only configures `publishTo` to write artefacts to a local directory. The actual remote publishing and unpublishing will be handled by build-jobs.

### Sbt 1.x

Since major version 1, this plugin is cross compiled for sbt 1.x (specifically 1.3.4).

| Sbt version | Plugin version |
| ----------- | -------------- |
| `0.13.x`    | `any`          |
| `>= 1.x`    | `>= 1.x`       |


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
