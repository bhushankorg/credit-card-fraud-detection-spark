package com.fraudDetection.spark.jobs

import com.fraudDetection.cassandra.CassandraConfig
import com.fraudDetection.config.Config
import com.fraudDetection.spark.{DataReader, DataBalancing, SparkConfig}
import com.fraudDetection.spark.algorithms.Algorithms
import com.fraudDetection.spark.pipeline.BuildPipeline
import org.apache.spark.ml.Pipeline




/**
 * Created by kafka on 9/5/18.
 */

object FraudDetectionTraining extends SparkJob("Balancing Fraud & Non-Fraud Dataset"){


  def main(args: Array[String]) {

    Config.parseArgs(args)

    import sparkSession.implicits._

    val fraudTransactionDF = DataReader.readFromCassandra(CassandraConfig.keyspace, CassandraConfig.fraudTransactionTable)
      .select("cc_num" , "category", "merchant", "distance", "amt", "age", "is_fraud")

    val nonFraudTransactionDF = DataReader.readFromCassandra(CassandraConfig.keyspace, CassandraConfig.nonFraudTransactionTable)
      .select("cc_num" , "category", "merchant", "distance", "amt", "age", "is_fraud")

    val transactionDF = nonFraudTransactionDF.union(fraudTransactionDF)
    transactionDF.cache()


    val coloumnNames = List("cc_num", "category", "merchant", "distance", "amt", "age")

    val pipelineStages = BuildPipeline.createFeaturePipeline(transactionDF.schema, coloumnNames)
    val pipeline = new Pipeline().setStages(pipelineStages)
    val PreprocessingTransformerModel = pipeline.fit(transactionDF)
    PreprocessingTransformerModel.save(SparkConfig.preprocessingModelPath)

    val featureDF = PreprocessingTransformerModel.transform(transactionDF)


    val fraudDF = featureDF
      .filter($"is_fraud" === 1)
      .withColumnRenamed("is_fraud", "label")
      .select("features", "label")

    val nonFraudDF = featureDF.filter($"is_fraud" === 0)
    val fraudCount = fraudDF.count()


    /* There will be very few fraud transaction and more normal transaction. Models created  from such
     * imbalanced data will not have good prediction accuracy. Hence balancing the dataset. K-means is used for balancing
     */
    val balancedNonFraudDF = DataBalancing.createBalancedDataframe(nonFraudDF, fraudCount.toInt)
    val finalfeatureDF = fraudDF.union(balancedNonFraudDF)


    val randomForestModel = Algorithms.randomForestClassifier(finalfeatureDF)
    randomForestModel.save(SparkConfig.modelPath)

  }

}
