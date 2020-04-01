name := "zuora-creditor"
description := "This project contains a set of services and Lambda functions which find negative invoices and converts " +
  "them into a credit balance on the user's account, so that the amount is discounted off their next positive bill"
version := "0.0.2"
scalaVersion := "2.11.12"
organization := "com.gu.zuora"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-target:jvm-1.8",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

lazy val root = (project in file(".")).enablePlugins(RiffRaffArtifact)

assemblyJarName := "zuora-creditor.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := "MemSub::Membership Admin::Zuora Creditor"

addCommandAlias("dist", ";riffRaffArtifact")

val jacksonVersion = "2.10.3"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-java-sdk-sns" % "1.11.754",
  "com.gu" %% "simple-configuration-ssm" % "1.4.1",
  "com.typesafe.play" %% "play-json" % "2.6.13",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "com.github.melrief" %% "purecsv" % "0.1.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

initialize := {
  val _ = initialize.value
  assert(sys.props("java.specification.version") == "1.8", "Java 8 is required for this project.")
}

resolvers += "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

