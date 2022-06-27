addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.9")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeRepo("public")