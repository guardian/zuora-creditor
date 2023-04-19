name := "zuora-creditor"
description := "This project contains a set of services and Lambda functions which find negative invoices and converts " +
  "them into a credit balance on the user's account, so that the amount is discounted off their next positive bill"
version := "0.0.2"
scalaVersion := "2.13.10"
organization := "com.gu.zuora"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

lazy val root = (project in file(".")).enablePlugins(RiffRaffArtifact)

assemblyJarName := "zuora-creditor.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := "MemSub::Membership Admin::Zuora Creditor"
riffRaffArtifactResources += (file("cloudformation.yaml"), "cfn/cfn.yaml")

addCommandAlias("dist", ";riffRaffArtifact")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "software.amazon.awssdk" % "sns" % "2.17.225",
  "com.gu" %% "simple-configuration-ssm" % "1.5.7",
  "com.typesafe.play" %% "play-json" % "2.9.4",
  "ch.qos.logback" % "logback-classic" % "1.4.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "io.kontainers" %% "purecsv" % "0.4.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.14.2",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.14.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.14.2",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _                                   => MergeStrategy.first
}
