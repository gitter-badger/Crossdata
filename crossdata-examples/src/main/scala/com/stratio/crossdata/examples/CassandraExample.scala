/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.stratio.crossdata.examples

import org.apache.spark.sql.crossdata.XDContext
import org.apache.spark.{SparkConf, SparkContext}

sealed trait DefaultConstants {
  val Cluster = "Test Cluster"
  //val Catalog = "highschool"
  val Catalog = "report"
  //val Table = "students"
  val Table = "cars"
  val CassandraHost = "127.0.0.1"
  val SourceProvider = "com.stratio.crossdata.sql.sources.cassandra"
  // Cassandra provider => org.apache.spark.sql.cassandra
}

object CassandraExample extends App with DefaultConstants {

  withCrossdataContext { xdContext =>

    xdContext.sql(
      "CREATE TEMPORARY TABLE " + Table + " USING " + SourceProvider + " OPTIONS " +
        "(keyspace \"" + Catalog + "\", table \"" + Table + "\", " +
        "cluster \"" + Cluster + "\", pushdown \"true\")".stripMargin)

    // scalastyle:off
    //xdContext.sql(s"SELECT comment as b FROM $Table WHERE comment = 1 AND id = 5").collect().foreach(print)
    //xdContext.sql(s"SELECT comment as b FROM $Table WHERE id = 1").collect().foreach(print)
    xdContext.sql(s"SELECT *  FROM $Table ").collect().foreach(print)
    //xdContext.sql(s"SELECT comment as b FROM $Table WHERE comment = 'A'").show(5)
    //xdContext.sql(s"SELECT comment as b FROM $Table WHERE id IN(1,2,3,4,5,6,7,8,9,10) limit 2").show(5)
    xdContext.sql(s"SELECT id as b FROM $Table WHERE id IN(1,2,3,4,5,6,7,8,9,10) limit 2").show(5)
    // scalastyle:on

  }


  private def withCrossdataContext(commands: XDContext => Unit) = {

    val sparkConf = new SparkConf().
      setAppName("CassandraExample").
      setMaster("local[4]").
      set("spark.cassandra.connection.host", CassandraHost)

    val sc = new SparkContext(sparkConf)

    try {
      val xdContext = new XDContext(sc)
      commands(xdContext)
    } finally {
      sc.stop()
    }

  }

}

