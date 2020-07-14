organization in ThisBuild := "xarvalus"
version in ThisBuild := "0.1.0"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % Test
val jwt = "com.pauldijou" %% "jwt-play-json" % "4.2.0"
val accord = "com.wix" %% "accord-core" % "0.7.4"

lagomKafkaPropertiesFile in ThisBuild :=
  Some((baseDirectory in ThisBuild).value / "project" / "kafka-server.properties")

lazy val `reactive-stock` = (project in file("."))
  .aggregate(
    `common`,
    `asset-api`, `asset-impl`,
    `table-api`, `table-impl`, `table-stream-api`, `table-stream-impl`,
    `trader-api`, `trader-impl`,
    `transaction-api`, `transaction-impl`,
  )

lazy val `common` = (project in file("common"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      jwt,
      accord
    )
  )

lazy val `asset-api` = (project in file("asset-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )
  .dependsOn(`common`)

lazy val `table-api` = (project in file("table-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )
  .dependsOn(`common`)

lazy val `table-stream-api` = (project in file("table-stream-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )
  .dependsOn(`common`, `table-api`)

lazy val `trader-api` = (project in file("trader-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      accord
    )
  )
  .dependsOn(`common`)

lazy val `transaction-api` = (project in file("transaction-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      accord
    )
  )
  .dependsOn(`common`)

lazy val `asset-impl` = (project in file("asset-impl"))
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
  .dependsOn(`common`, `asset-api`, `table-api`)

lazy val `table-impl` = (project in file("table-impl"))
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
  .dependsOn(`common`, `table-api`, `transaction-api`)

lazy val `table-stream-impl` = (project in file("table-stream-impl"))
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
  .dependsOn(`common`, `table-stream-api`, `table-api`)

lazy val `trader-impl` = (project in file("trader-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest,
      jwt
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`common`, `trader-api`)

lazy val `transaction-impl` = (project in file("transaction-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest,
      jwt
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`common`, `transaction-api`)
