import java.util

import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.ml.regression.{RandomForestRegressionModel, RandomForestRegressor}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.json4s.{Formats, NoTypeHints}
import org.json4s.native.Serialization
import org.json4s.native.Serialization.read

// Messages passed by Kafka from reactive-stock
case class ResolvedTransaction(
  transactionId: String,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  timestamp: String,
  buyer: String, // UUID
  seller: String // UUID
) extends Serializable

// Topic from reactive-stock
object ResolvedTransaction {
  val Topics = Array("resolved_transactions")

  // Reference: http://kafka.apache.org/documentation.html#consumerconfigs
  val KafkaParams: Map[String, Object] = Map(
    // Hostname used internally by docker
    // On host machine we are mapping `host.docker.internal` back to localhost in /etc/hosts
    // TODO: fix to better solution, i.e. with advertised.listeners
    "bootstrap.servers" -> "host.docker.internal:9092",
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[ResolvedTransactionDeserializer].getCanonicalName,
    "group.id" -> "spark-ml-analysis",
    "auto.offset.reset" -> "earliest", // TODO: previously "latest"
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )
}

class ResolvedTransactionDeserializer extends Deserializer[ResolvedTransaction] {
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def deserialize(topic: String, data: Array[Byte]): ResolvedTransaction = {
    val jsonString = new StringDeserializer().deserialize(topic, data)
    read[ResolvedTransaction](jsonString)
  }

  override def close(): Unit = {}
}

// Based on: https://spark.apache.org/docs/latest/ml-classification-regression.html#random-forest-regression
object TransactionRegressorModel {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder
      .appName("TransactionRegressorModel")
      .getOrCreate()

    val streamingContext = new StreamingContext(spark.sparkContext, Seconds(5))

    val stream = KafkaUtils.createDirectStream[String, ResolvedTransaction](
      streamingContext,
      PreferConsistent,
      Subscribe[String, ResolvedTransaction]
        (ResolvedTransaction.Topics, ResolvedTransaction.KafkaParams)
    )

    stream.foreachRDD(record => {
      if (record.count() == 0) {
        streamingContext.stop()
      }

      record.foreach(some =>
        println("Value from Kafka event", some.value().price))
    })

    // TODO: temporary disabled
    //streamingContext.start()



    // TODO: make regression model
    //  ->> https://www.geeksforgeeks.org/random-forest-regression-in-python/
    //     https://towardsdatascience.com/random-forest-and-its-implementation-71824ced454f
    //     http://blog.yhat.com/posts/random-forests-in-python.html
    //    https://towardsdatascience.com/an-introduction-to-random-forest-using-tesla-stock-prices-d9c6e113be3c
    //    https://blog.quantinsti.com/random-forest-algorithm-in-python/
    //  https://medium.com/rahasak/random-forest-classifier-with-apache-spark-c63b4a23a7cc
    //  https://dzone.com/articles/random-forest-as-a-regressor-a-spark-based-solutio
    //  https://runawayhorse001.github.io/LearningApacheSpark/regression.html#random-forest-regression
    // Train on quantity & timestamp as unix num? predict for future quantity & timestamp?
    // what about future coming values? compare with previous predictions?
    // stay as infinity stream model, retrain if rmse is low?



    // Load and parse the data file, converting it to a DataFrame.
    val data = spark.read
      .format("libsvm")
      .load("../spark-ml-analysis/src/main/resources/sample_libsvm_data.txt")

    // Automatically identify categorical features, and index them.
    // Set maxCategories so features with > 4 distinct values are treated as continuous.
    val featureIndexer = new VectorIndexer()
      .setInputCol("features")
      .setOutputCol("indexedFeatures")
      .setMaxCategories(4)
      .fit(data)

    // Split the data into training and test sets (30% held out for testing).
    val Array(trainingData, testData) = data.randomSplit(Array(0.7, 0.3))

    // Train a RandomForest model.
    val rf = new RandomForestRegressor()
      .setLabelCol("label")
      .setFeaturesCol("indexedFeatures")

    // Chain indexer and forest in a Pipeline.
    val pipeline = new Pipeline()
      .setStages(Array(featureIndexer, rf))

    // Train model. This also runs the indexer.
    val model = pipeline.fit(trainingData)

    // Make predictions.
    val predictions = model.transform(testData)

    // Select example rows to display.
    predictions.select("prediction", "label", "features").show(100)

    // Select (prediction, true label) and compute test error.
    val evaluator = new RegressionEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("rmse")
    val rmse = evaluator.evaluate(predictions)
    println(s"Root Mean Squared Error (RMSE) on test data = $rmse")

    val rfModel = model.stages(1).asInstanceOf[RandomForestRegressionModel]
    println(s"Learned regression forest model:\n ${rfModel.toDebugString}")

    try {
      streamingContext.awaitTermination()
    } catch {
      case _: Exception =>
        println("Streaming exception")
    } finally {
      println("Streaming terminated")
    }

    spark.stop()
  }
}
