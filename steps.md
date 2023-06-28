**Pre-Requisites**

- Docker & Docker-Compose
- Java
- Maven

## Create a Docker Compose File

```yaml
###########################################
# Flink - Iceberg - Nessie Setup
###########################################

version: "3"

services:
  # Spark Notebook Server
  spark-iceberg:
    image: alexmerced/spark34notebook
    container_name: spark-iceberg
    networks:
      iceberg-nessie-flink-net:
    depends_on:
      - catalog
      - storage
    volumes:
      - ./warehouse:/home/docker/warehouse
      - ./notebooks:/home/docker/notebooks
    environment:
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
      - AWS_DEFAULT_REGION=us-east-1
    ports:
      - 8888:8888
      - 8080:8080
      - 10000:10000
      - 10001:10001
  # Nessie Catalog Server Using In-Memory Store
  catalog:
    image: projectnessie/nessie
    container_name: catalog
    networks:
      iceberg-nessie-flink-net:
    ports:
      - 19120:19120
  # Minio Storage Server
  storage:
    image: minio/minio
    container_name: storage
    environment:
      - MINIO_ROOT_USER=admin
      - MINIO_ROOT_PASSWORD=password
      - MINIO_DOMAIN=storage
      - MINIO_REGION_NAME=us-east-1
      - MINIO_REGION=us-east-1
    networks:
      iceberg-nessie-flink-net:
    ports:
      - 9001:9001
      - 9000:9000
    command: ["server", "/data", "--console-address", ":9001"]
  # Minio Client Container
  mc:
    depends_on:
      - storage
    image: minio/mc
    container_name: mc
    networks:
      iceberg-nessie-flink-net:
        aliases:
          - minio.storage
    environment:
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
      - AWS_DEFAULT_REGION=us-east-1
    entrypoint: >
      /bin/sh -c "
      until (/usr/bin/mc config host add minio http://storage:9000 admin password) do echo '...waiting...' && sleep 1; done;
      /usr/bin/mc rm -r --force minio/warehouse;
      /usr/bin/mc mb minio/warehouse;
      /usr/bin/mc mb minio/iceberg;
      /usr/bin/mc policy set public minio/warehouse;
      /usr/bin/mc policy set public minio/iceberg;
      tail -f /dev/null
      "
  # Flink Job Manager
  flink-jobmanager:
    image: alexmerced/flink-iceberg:latest
    ports:
      - "8081:8081"
    command: jobmanager
    networks:
      iceberg-nessie-flink-net:
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: flink-jobmanager
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
      - AWS_DEFAULT_REGION=us-east-1
      - S3_ENDPOINT=http://minio.storage:9000
      - S3_PATH_STYLE_ACCESS=true
  # Flink Task Manager
  flink-taskmanager:
    image: alexmerced/flink-iceberg:latest
    depends_on:
      - flink-jobmanager
    command: taskmanager
    networks:
      iceberg-nessie-flink-net:
    scale: 1
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: flink-jobmanager
        taskmanager.numberOfTaskSlots: 2
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
      - AWS_DEFAULT_REGION=us-east-1
      - S3_ENDPOINT=http://minio.storage:9000
      - S3_PATH_STYLE_ACCESS=true
networks:
  iceberg-nessie-flink-net:
```

## Create A Flink Job

- navigate terminal to an empty directory

- create a new maven project

```shell
mvn archetype:generate
```

- choose what you want as groupId (usually a domain in reverse like "com.xyz") and artifactId (name of the project like "flink-iceberg-job") for everything else you can choose the defaults

- You'll have three files available

```
/{artifactId}/pom.xml # Tracks Dependencies
/{artifactId}/src/main/java/{groupId}/App.java # Main File
/{artifactId}/src/test/java/{groupId}/AppTest.java # unit-tests
```

- Then within the `<Dependencies></Dependencies>` section of the pom.xml add the following

```xml
  <dependencies>
  <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-java</artifactId>
        <version>1.16.1</version>
    </dependency>
    <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-streaming-java</artifactId>
        <version>1.16.1</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>bundle</artifactId>
        <version>2.20.18</version>
    </dependency>
    <dependency>
        <groupId>org.apache.iceberg</groupId>
        <artifactId>iceberg-flink</artifactId>
        <version>1.3.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-table-api-java-bridge</artifactId>
        <version>1.16.1</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-table-api-java</artifactId>
        <version>1.16.1</version>
    </dependency>
    <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-table-common</artifactId>
        <version>1.16.1</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.iceberg</groupId>
        <artifactId>iceberg-flink-runtime-1.12</artifactId>
        <version>0.13.2</version>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>url-connection-client</artifactId>
        <version>2.20.18</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.iceberg</groupId>
        <artifactId>iceberg-core</artifactId>
        <version>1.3.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.hadoop</groupId>
        <artifactId>hadoop-common</artifactId>
        <version>2.8.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.iceberg</groupId>
        <artifactId>iceberg-nessie</artifactId>
        <version>1.3.0</version>
    </dependency>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.11</version>
      <scope>test</scope>
    </dependency>
    <dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-connector-files</artifactId>
    <version>1.17.1</version>
</dependency>
  </dependencies>
```

Create a class for your data

```java
package com.dremio_developer_advocacy;

public class ExampleData {
    private Long id;
    private String data;

    public ExampleData() {
    }

    public ExampleData(Long id, String data) {
        this.id = id;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
```

The Create a Class for Your Flink Job:

```java
package com.dremio_developer_advocacy;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import static org.apache.flink.table.api.Expressions.$;
import com.dremio_developer_advocacy.ExampleData;

public class Main {
    public static void main(String[] args) throws Exception {

        // Configure the S3 file system
        // Configuration config = new Configuration();
        // config.setString("s3.access-key", "ryocQXbviUDyuV4qkLyD");
        // config.setString("s3.secret-key",
        // "0PkHnmmqzy4bFRtlXfp5i7XJPyrB9cl2nufpBcwU");
        // config.setString("s3.endpoint", "http://storage:9000");
        // config.setBoolean("s3.path.style.access", true);

        System.setProperty("fs.s3a.endpoint", "http://storage:9000");
        System.setProperty("fs.s3a.access.key", "admin");
        System.setProperty("fs.s3a.secret.key", "password");
        System.setProperty("fs.s3a.path.style.access", "true");

        // set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // set up the table environment
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(
                env,
                EnvironmentSettings.newInstance().inStreamingMode().build());

        // create the Nessie catalog
        tableEnv.executeSql(
                "CREATE CATALOG iceberg WITH ("
                        + "'type'='iceberg',"
                        + "'catalog-impl'='org.apache.iceberg.nessie.NessieCatalog',"
                        + "'io-impl'='org.apache.iceberg.aws.s3.S3FileIO',"
                        + "'uri'='http://catalog:19120/api/v1',"
                        + "'authentication.type'='none',"
                        + "'ref'='main',"
                        + "'client.assume-role.region'='us-east-1',"
                        + "'warehouse' = 's3://warehouse',"
                        + "'s3.endpoint'='http://172.30.0.3:9000'"
                        + ")");

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
                        + ")");

        // create a DataStream of Tuple2 (equivalent to Row of 2 fields)
        DataStream<Tuple2<Long, String>> dataStream = env.fromElements(
                Tuple2.of(1L, "foo"),
                Tuple2.of(1L, "bar"),
                Tuple2.of(1L, "baz"));

        // apply a map transformation to convert the Tuple2 to an ExampleData object
        DataStream<ExampleData> mappedStream = dataStream.map(new MapFunction<Tuple2<Long, String>, ExampleData>() {
            @Override
            public ExampleData map(Tuple2<Long, String> value) throws Exception {
                // perform your mapping logic here and return a ExampleData instance
                // for example:
                return new ExampleData(value.f0, value.f1);
            }
        });

        // convert the DataStream to a Table
        Table table = tableEnv.fromDataStream(mappedStream, $("id"), $("data"));

        // // convert the DataStream to a Table
        // Table table = tableEnv.fromDataStream(dataStream, $("id"), $("data"));

        // register the Table as a temporary view
        tableEnv.createTemporaryView("my_datastream", table);

        // write the DataStream to the table
        tableEnv.executeSql(
                "INSERT INTO db.table1 SELECT * FROM my_datastream");
    }
}

```

- make sure to use the ip address of the minio container
  - run `docker exec -it storage /bin/bash` in a new terminal to get into storage containers shell
  - run `ifconfig` and get the inet value which is the containers ip address on the network

- compile the package with `mvn package`
- submit the jar to the jobmanager on localhost:8081