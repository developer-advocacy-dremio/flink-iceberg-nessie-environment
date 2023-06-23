package com.dremio_developer_advocacy;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class Main {
    public static void main(String[] args) throws Exception {

        // set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // set up the table environment
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(
                env,
                EnvironmentSettings.newInstance().useBlinkPlanner().inStreamingMode().build()
        );

        // create the Nessie catalog
        tableEnv.executeSql(
            "CREATE CATALOG my_catalog WITH ("
            + "'type'='iceberg',"
            + "'catalog-impl'='org.apache.iceberg.nessie.NessieCatalog',"
            + "'uri'='http://localhost:19120/api/v1',"
            + "'ref'='main',"
            + "'warehouse' = '/path/to/flink/warehouse'"
            + ")"
        );

        // create the table
        tableEnv.executeSql(
            "CREATE TABLE `my_catalog`.`my_database`.`my_table` ("
            + "id BIGINT COMMENT 'unique id',"
            + "data STRING"
            + ")"
        );

        // create a DataStream of Tuple2 (equivalent to Row of 2 fields)
        DataStream<Tuple2<Long, String>> dataStream = env.fromElements(
            Tuple2.of(1L, "foo"),
            Tuple2.of(2L, "bar"),
            Tuple2.of(3L, "baz")
        );

        // register the DataStream as a table in the TableEnvironment
        tableEnv.createTemporaryView("my_datastream", dataStream, $("id"), $("data"));

        // write the DataStream to the table
        tableEnv.executeSql(
            "INSERT INTO `my_catalog`.`my_database`.`my_table`"
            + "SELECT * FROM my_datastream"
        );

        env.execute("Flink Streaming Java API Skeleton");
    }
}