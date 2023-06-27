**Pre-Requisites**

- Docker & Docker-Compose
- Java
- Maven

## Create a Docker Compose File

```yml
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
      iceberg_net:
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
      iceberg_net:
    ports:
      - 19120:19120
  # Minio Storage Server
  storage:
    image: minio/minio
    container_name: storage
      - MINIO_ROOT_USER=admin
      - MINIO_ROOT_PASSWORD=password
      - MINIO_DOMAIN=minio
    networks:
      iceberg_net:
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
      iceberg_net:
    environment:
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
    entrypoint: >
      /bin/sh -c "
      until (/usr/bin/mc config host add minio http://storage:9000 admin password) do echo '...waiting...' && sleep 1; done;
      /usr/bin/mc rm -r --force minio/warehouse;
      /usr/bin/mc mb minio/warehouse;
      /usr/bin/mc policy set public minio/warehouse;
      tail -f /dev/null
      "
  # Flink Job Manager
  flink-jobmanager:
    image: flink:latest
    ports:
      - "8081:8081"
    command: jobmanager
    networks:
      iceberg_net:
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: flink-jobmanager
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
  # Flink Task Manager
  flink-taskmanager:
    image: flink:latest
    depends_on:
      - flink-jobmanager
    command: taskmanager
    networks:
      iceberg_net:
    scale: 1
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: flink-jobmanager
        taskmanager.numberOfTaskSlots: 2
      - AWS_ACCESS_KEY_ID=admin
      - AWS_SECRET_ACCESS_KEY=password
      - AWS_REGION=us-east-1
networks:
  iceberg_net:
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
<dependency>
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
  </dependencies>
```

Here is the reference code for your class:

```java
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import static org.apache.flink.table.api.Expressions.$;

public class Main {
    public static void main(String[] args) throws Exception {

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
            + "'catalog-impl'='org.apache.iceberg.nessie.NessieCatalog',"
            + "'uri'='http://catalog:19120/api/v1',"
            + "'auth'='none',"
            + "'ref'='main',"
            + "'warehouse' = '/warehouse'"
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
            Tuple2.of(1L, "bar"),
            Tuple2.of(1L, "baz")
        );

        // convert the DataStream to a Table
        Table table = tableEnv.fromDataStream(dataStream, $("id"), $("data"));

        // register the Table as a temporary view
        tableEnv.createTemporaryView("my_datastream", table);

        // write the DataStream to the table
        tableEnv.executeSql(
            "INSERT INTO `my_catalog`.`my_database`.`my_table`"
            + "SELECT * FROM my_datastream"
        );

        env.execute("Flink Streaming Java API Skeleton");
    }
}
```