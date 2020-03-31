name := "zuora-creditor"
description:= "This project contains a set of services and Lambda functions which find negative invoices and converts " +
  "them into a credit balance on the user's account, so that the amount is discounted off their next positive bill"
version       := "0.0.2"
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

val jacksonVersion = "2.9.9"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-java-sdk-kms" % "1.11.566",
  "com.typesafe.play" %% "play-json" % "2.6.13",
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "com.github.melrief" %% "purecsv" % "0.1.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,  // updated for snyk: https://app.snyk.io/org/the-guardian-cuu/project/c8aa728c-1f6a-4ede-900c-dfde0c0af9bf/
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,  // updated for snyk: https://app.snyk.io/org/the-guardian-cuu/project/c8aa728c-1f6a-4ede-900c-dfde0c0af9bf/
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9.1",
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion, // updated for snyk: https://app.snyk.io/org/the-guardian-cuu/project/c8aa728c-1f6a-4ede-900c-dfde0c0af9bf/
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,  // updated for snyk: https://app.snyk.io/org/the-guardian-cuu/project/c8aa728c-1f6a-4ede-900c-dfde0c0af9bf/
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

initialize := {
  val _ = initialize.value
  assert(sys.props("java.specification.version") == "1.8", "Java 8 is required for this project.")
}
