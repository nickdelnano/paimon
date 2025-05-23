/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.spark.sql

import org.apache.paimon.data.{BinaryString, GenericRow, Timestamp}
import org.apache.paimon.manifest.ManifestCommittable
import org.apache.paimon.spark.PaimonHiveTestBase
import org.apache.paimon.utils.DateTimeUtils

import org.apache.spark.sql.{DataFrame, Row}

import java.time.LocalDateTime
import java.util.Collections

class TableValuedFunctionsTest extends PaimonHiveTestBase {

  withPk.foreach {
    hasPk =>
      bucketModes.foreach {
        bucket =>
          test(s"incremental query: hasPk: $hasPk, bucket: $bucket") {
            Seq("paimon", sparkCatalogName, paimonHiveCatalogName).foreach {
              catalogName =>
                sql(s"use $catalogName")

                withTable("t") {
                  val prop = if (hasPk) {
                    s"'primary-key'='a,b', 'bucket' = '$bucket' "
                  } else if (bucket != -1) {
                    s"'bucket-key'='b', 'bucket' = '$bucket' "
                  } else {
                    "'write-only'='true'"
                  }

                  spark.sql(s"""
                               |CREATE TABLE t (a INT, b INT, c STRING)
                               |USING paimon
                               |TBLPROPERTIES ($prop)
                               |PARTITIONED BY (a)
                               |""".stripMargin)

                  spark.sql("INSERT INTO t values (1, 1, '1'), (2, 2, '2')")
                  spark.sql("INSERT INTO t VALUES (1, 3, '3'), (2, 4, '4')")
                  spark.sql("INSERT INTO t VALUES (1, 5, '5'), (1, 7, '7')")

                  checkAnswer(
                    incrementalDF("t", 0, 1).orderBy("a", "b"),
                    Row(1, 1, "1") :: Row(2, 2, "2") :: Nil)
                  checkAnswer(
                    spark.sql(
                      "SELECT * FROM paimon_incremental_query('t', '0', '1') ORDER BY a, b"),
                    Row(1, 1, "1") :: Row(2, 2, "2") :: Nil)

                  checkAnswer(
                    incrementalDF("t", 1, 2).orderBy("a", "b"),
                    Row(1, 3, "3") :: Row(2, 4, "4") :: Nil)
                  checkAnswer(
                    spark.sql(
                      "SELECT * FROM paimon_incremental_query('t', '1', '2') ORDER BY a, b"),
                    Row(1, 3, "3") :: Row(2, 4, "4") :: Nil)

                  checkAnswer(
                    incrementalDF("t", 2, 3).orderBy("a", "b"),
                    Row(1, 5, "5") :: Row(1, 7, "7") :: Nil)
                  checkAnswer(
                    spark.sql(
                      "SELECT * FROM paimon_incremental_query('t', '2', '3') ORDER BY a, b"),
                    Row(1, 5, "5") :: Row(1, 7, "7") :: Nil)

                  checkAnswer(
                    incrementalDF("t", 1, 3).orderBy("a", "b"),
                    Row(1, 3, "3") :: Row(1, 5, "5") :: Row(1, 7, "7") :: Row(2, 4, "4") :: Nil
                  )
                  checkAnswer(
                    spark.sql(
                      "SELECT * FROM paimon_incremental_query('t', '1', '3') ORDER BY a, b"),
                    Row(1, 3, "3") :: Row(1, 5, "5") :: Row(1, 7, "7") :: Row(2, 4, "4") :: Nil)
                }
            }
          }
      }
  }

  test("Table Valued Functions: paimon_incremental_between_timestamp") {
    Seq("paimon", sparkCatalogName, paimonHiveCatalogName).foreach {
      catalogName =>
        sql(s"USE $catalogName")
        val dbName = "test_tvf_db"
        withDatabase(dbName) {
          sql(s"CREATE DATABASE $dbName")
          withTable("t") {
            sql(s"USE $dbName")
            sql("CREATE TABLE t (id INT) USING paimon")

            sql("INSERT INTO t VALUES 1")
            Thread.sleep(100)
            val t1 = System.currentTimeMillis()
            sql("INSERT INTO t VALUES 2")
            Thread.sleep(100)
            val t2 = System.currentTimeMillis()
            sql("INSERT INTO t VALUES 3")
            sql("INSERT INTO t VALUES 4")
            Thread.sleep(100)
            val t3 = System.currentTimeMillis()
            sql("INSERT INTO t VALUES 5")

            checkAnswer(
              sql(
                s"SELECT * FROM paimon_incremental_between_timestamp('t', '$t1', '$t2') ORDER BY id"),
              Seq(Row(2)))
            checkAnswer(
              sql(
                s"SELECT * FROM paimon_incremental_between_timestamp('$dbName.t', '$t2', '$t3') ORDER BY id"),
              Seq(Row(3), Row(4)))
            checkAnswer(
              sql(
                s"SELECT * FROM paimon_incremental_between_timestamp('$catalogName.$dbName.t', '$t1', '$t3') ORDER BY id"),
              Seq(Row(2), Row(3), Row(4)))
            val t1String = DateTimeUtils.formatLocalDateTime(DateTimeUtils.toLocalDateTime(t1), 3)
            val t3String = DateTimeUtils.formatLocalDateTime(DateTimeUtils.toLocalDateTime(t3), 3)
            checkAnswer(
              sql(
                s"SELECT * FROM paimon_incremental_between_timestamp('$catalogName.$dbName.t', '$t1String', '$t3String') ORDER BY id"),
              Seq(Row(2), Row(3), Row(4)))
          }
        }
    }
  }

  test("Table Valued Functions: paimon_incremental_to_auto_tag") {
    withTable("t") {
      sql("""
            |CREATE TABLE t (a INT, b STRING) USING paimon
            |TBLPROPERTIES ('primary-key' = 'a', 'bucket' = '1', 'tag.automatic-creation'='watermark', 'tag.creation-period'='daily')
            |""".stripMargin)

      val table = loadTable("t")
      val write = table.newWrite(commitUser)
      val commit = table.newCommit(commitUser).ignoreEmptyCommit(false)

      write.write(GenericRow.of(1, BinaryString.fromString("a")))
      var commitMessages = write.prepareCommit(false, 0)
      commit.commit(
        new ManifestCommittable(
          0,
          utcMills("2024-12-02T10:00:00"),
          Collections.emptyMap[Integer, java.lang.Long],
          commitMessages))

      write.write(GenericRow.of(2, BinaryString.fromString("b")))
      commitMessages = write.prepareCommit(false, 1)
      commit.commit(
        new ManifestCommittable(
          1,
          utcMills("2024-12-03T10:00:00"),
          Collections.emptyMap[Integer, java.lang.Long],
          commitMessages))

      write.write(GenericRow.of(3, BinaryString.fromString("c")))
      commitMessages = write.prepareCommit(false, 2)
      commit.commit(
        new ManifestCommittable(
          2,
          utcMills("2024-12-05T10:00:00"),
          Collections.emptyMap[Integer, java.lang.Long],
          commitMessages))

      checkAnswer(
        sql(s"SELECT * FROM paimon_incremental_to_auto_tag('t', '2024-12-01') ORDER BY a"),
        Seq())
      checkAnswer(
        sql(s"SELECT * FROM paimon_incremental_to_auto_tag('t', '2024-12-02') ORDER BY a"),
        Seq(Row(2, "b")))
      checkAnswer(
        sql(s"SELECT * FROM paimon_incremental_to_auto_tag('t', '2024-12-03') ORDER BY a"),
        Seq())
      checkAnswer(
        sql(s"SELECT * FROM paimon_incremental_to_auto_tag('t', '2024-12-04') ORDER BY a"),
        Seq(Row(3, "c")))
    }
  }

  test("Table Valued Functions: incremental query with inconsistent tag bucket") {
    withTable("t") {
      sql("""
            |CREATE TABLE t (a INT, b INT) USING paimon
            |TBLPROPERTIES ('primary-key'='a', 'bucket' = '1')
            |""".stripMargin)

      val table = loadTable("t")

      sql("INSERT INTO t VALUES (1, 11), (2, 22)")
      table.createTag("2024-01-01", 1)

      sql("ALTER TABLE t SET TBLPROPERTIES ('bucket' = '2')")
      sql("INSERT OVERWRITE t SELECT * FROM t")

      sql("INSERT INTO t VALUES (3, 33)")
      table.createTag("2024-01-03", 3)

      sql("DELETE FROM t WHERE a = 1")
      table.createTag("2024-01-04", 4)

      sql("UPDATE t SET b = 222 WHERE a = 2")
      table.createTag("2024-01-05", 5)

      checkAnswer(
        sql(
          "SELECT * FROM paimon_incremental_query('t', '2024-01-01', '2024-01-03') ORDER BY a, b"),
        Seq(Row(3, 33)))

      checkAnswer(
        sql("SELECT * FROM paimon_incremental_to_auto_tag('t', '2024-01-03') ORDER BY a, b"),
        Seq(Row(3, 33)))

      checkAnswer(
        sql(
          "SELECT * FROM paimon_incremental_query('t', '2024-01-01', '2024-01-04') ORDER BY a, b"),
        Seq(Row(3, 33)))

      checkAnswer(
        sql(
          "SELECT * FROM paimon_incremental_query('t', '2024-01-01', '2024-01-05') ORDER BY a, b"),
        Seq(Row(2, 222), Row(3, 33)))

      checkAnswer(
        sql(
          "SELECT * FROM paimon_incremental_query('`t$audit_log`', '2024-01-01', '2024-01-04') ORDER BY a, b"),
        Seq(Row("-D", 1, 11), Row("+I", 3, 33)))

      checkAnswer(
        sql(
          "SELECT * FROM paimon_incremental_query('`t$audit_log`', '2024-01-01', '2024-01-05') ORDER BY a, b"),
        Seq(Row("-D", 1, 11), Row("+U", 2, 222), Row("+I", 3, 33))
      )
    }
  }

  test("Table Valued Functions: incremental query with delete after minor compact") {
    withTable("t") {
      sql("""
            |CREATE TABLE t (id INT) USING paimon
            |TBLPROPERTIES ('primary-key'='id', 'bucket' = '1', 'write-only' = 'true')
            |""".stripMargin)

      sql("INSERT INTO t SELECT /*+ REPARTITION(1) */ id FROM range (1, 100001)")
      sql("CALL sys.compact(table => 't')")
      sql("INSERT INTO t VALUES 100001")
      sql("INSERT INTO t VALUES 100002")
      sql("CALL sys.create_tag('t', 'tag1')")

      sql(
        "CALL sys.compact(table => 't', compact_strategy => 'minor', options => 'num-sorted-run.compaction-trigger=2')")
      sql("DELETE FROM t WHERE id = 999")
      sql("CALL sys.create_tag('t', 'tag2')")

      //            tag1                          tag2
      // l0         f(+I 10001),f(+I 10002)       f(-D 999)
      // l1
      // l2
      // l3
      // l4                                       f(+I 10001,10002)
      // l5         f(+I 1-10000)                 f(+I 1-10000)
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag1' ORDER BY level"),
        Seq(Row(0), Row(0), Row(5)))
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag2' ORDER BY level"),
        Seq(Row(0), Row(4), Row(5)))

      // before files: f(+I 10001), f(+I 10002)
      // after files:  f(-D 999),   f(+I 10001,10002)
      checkAnswer(
        sql("SELECT * FROM paimon_incremental_query('`t$audit_log`', 'tag1', 'tag2') ORDER BY id"),
        Seq(Row("-D", 999)))
    }
  }

  test("Table Valued Functions: incremental query with delete after compact") {
    withTable("t") {
      sql("""
            |CREATE TABLE t (id INT) USING paimon
            |TBLPROPERTIES ('primary-key'='id', 'bucket' = '1', 'write-only' = 'true')
            |""".stripMargin)

      sql("INSERT INTO t VALUES 1")
      sql("INSERT INTO t VALUES 2")
      sql("CALL sys.create_tag('t', 'tag1')")

      sql("CALL sys.compact(table => 't')")
      sql("DELETE FROM t WHERE id = 1")
      sql("CALL sys.create_tag('t', 'tag2')")

      //         tag1                    tag2
      // l0      f(+I 1),f(+I 2)         f(-D 1)
      // l1
      // l2
      // l3
      // l4
      // l5                              f(+I 1,2)
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag1' ORDER BY level"),
        Seq(Row(0), Row(0)))
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag2' ORDER BY level"),
        Seq(Row(0), Row(5)))

      checkAnswer(
        sql("SELECT * FROM paimon_incremental_query('`t$audit_log`', 'tag1', 'tag2') ORDER BY id"),
        Seq(Row("-D", 1)))
    }
  }

  test("Table Valued Functions: incremental query with delete after compact2") {
    withTable("t") {
      sql("""
            |CREATE TABLE t (id INT) USING paimon
            |TBLPROPERTIES ('primary-key'='id', 'bucket' = '1', 'write-only' = 'true')
            |""".stripMargin)

      sql("INSERT INTO t VALUES 1")
      sql("DELETE FROM t WHERE id = 1")
      sql("CALL sys.create_tag('t', 'tag1')")

      sql("CALL sys.compact(table => 't')")
      sql("INSERT INTO t VALUES 1")
      sql("DELETE FROM t WHERE id = 1")
      sql("CALL sys.create_tag('t', 'tag2')")

      //         tag1                    tag2
      // l0      f(+I 1),f(-D 1)         f(+I 1),f(-D 1)
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag1' ORDER BY level"),
        Seq(Row(0), Row(0)))
      checkAnswer(
        sql("SELECT level FROM `t$files` VERSION AS OF 'tag2' ORDER BY level"),
        Seq(Row(0), Row(0)))

      checkAnswer(
        sql("SELECT * FROM paimon_incremental_query('`t$audit_log`', 'tag1', 'tag2') ORDER BY id"),
        Seq())
    }
  }

  private def incrementalDF(tableIdent: String, start: Int, end: Int): DataFrame = {
    spark.read
      .format("paimon")
      .option("incremental-between", s"$start,$end")
      .table(tableIdent)
  }

  private def utcMills(timestamp: String) =
    Timestamp.fromLocalDateTime(LocalDateTime.parse(timestamp)).getMillisecond
}
