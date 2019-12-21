organization in ThisBuild := "xarvalus"
version in ThisBuild := "0.1.0"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % Test

lazy val `reactive-stock` = (project in file("."))
  .aggregate(`reactive-stock-api`, `reactive-stock-impl`, `reactive-stock-stream-api`, `reactive-stock-stream-impl`)

lazy val `reactive-stock-api` = (project in file("reactive-stock-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `reactive-stock-impl` = (project in file("reactive-stock-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`reactive-stock-api`)

lazy val `reactive-stock-stream-api` = (project in file("reactive-stock-stream-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `reactive-stock-stream-impl` = (project in file("reactive-stock-stream-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`reactive-stock-stream-api`, `reactive-stock-api`)
