package com.dremio_developer_advocacy;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import static org.apache.flink.table.api.Expressions.$;

public class HadoopIceberg {
    public static void main(String[] args) throws Exception {

        // Configure the S3 file system
        // Configuration config = new Configuration();
        // config.setString("s3.access-key", "ryocQXbviUDyuV4qkLyD");
        // config.setString("s3.secret-key", "0PkHnmmqzy4bFRtlXfp5i7XJPyrB9cl2nufpBcwU");
        // config.setString("s3.endpoint", "http://storage:9000");
        // config.setBoolean("s3.path.style.access", true);

        // System.setProperty("fs.s3a.endpoint", "http://storage:9000");
        // System.setProperty("fs.s3a.access.key", "admin");
        // System.setProperty("fs.s3a.secret.key", "password");
        // System.setProperty("fs.s3a.path.style.access", "true");

        // set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // set up the table environment
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(
                env,
                EnvironmentSettings.newInstance().inStreamingMode().build()
        );

        // create the Nessie catalog
        tableEnv.executeSql(
            "CREATE CATALOG iceberg WITH ("
            + "'type'='iceberg',"
            + "'catalog-type'='hadoop',"
            + "'warehouse' = '/warehouse'"
            + ")"
        );

        // List all catalogs
        TableResult result = tableEnv.executeSql("SHOW CATALOGS");

        // Print the result to standard out
        result.print();

        // Set the current catalog to the new catalog
        tableEnv.useCatalog("iceberg");

        // Create a database in the current catalog
        tableEnv.executeSql("CREATE DATABASE IF NOT EXISTS db");

        // create the table
        tableEnv.executeSql(
            "CREATE TABLE IF NOT EXISTS db.table1 ("
            + "id BIGINT COMMENT 'unique id',"
            + "data STRING"
            + ")"
        );



 // create a DataStream of Tuple2 (equivalent to Row of 2 fields)
        DataStream<Tuple2<Long, String>> dataStream = env.fromElements(
            Tuple2.of(1L, "foo"),
            Tuple2.of(1L, "bar"),
            Tuple2.of(1L, "baz")
        );

        // convert the DataStream to a Table
        Table table = tableEnv.fromDataStream(dataStream, $("id"), $("data"));

        // register the Table as a temporary view
        tableEnv.createTemporaryView("my_datastream", table);

        // write the DataStream to the table
        tableEnv.executeSql(
            "INSERT INTO db.table1 SELECT * FROM my_datastream"
        );

        env.execute("Flink Streaming Java API Skeleton");
    }
}