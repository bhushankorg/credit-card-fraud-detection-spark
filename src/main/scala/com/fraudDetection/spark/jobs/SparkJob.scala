package com.fraudDetection.spark.jobs


import com.fraudDetection.spark.SparkConfig
import org.apache.spark.sql.SparkSession

/**
 * Created by kafka on 9/5/18.
 */
abstract class SparkJob(appName:String) {


  lazy implicit val sparkSession = SparkSession.builder
    .config(SparkConfig.sparkConf)
    .getOrCreate()

}