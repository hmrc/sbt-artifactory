resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("uk.gov.hmrc"     % "sbt-auto-build"     % "2.15.0")
addSbtPlugin("uk.gov.hmrc"     % "sbt-git-versioning" % "2.2.0")
addSbtPlugin("uk.gov.hmrc"     % "sbt-artifactory"    % "1.6.0")
addSbtPlugin("io.get-coursier" % "sbt-shading"        % "2.0.0")
