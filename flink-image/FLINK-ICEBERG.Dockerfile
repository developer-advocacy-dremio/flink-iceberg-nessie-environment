# Start from the official Flink image
FROM flink:1.16.1-scala_2.12

###############################################
## Add Startup Script And Make Executable
###############################################

# COPY ./initiate-flink.sh /opt/flink/bin/initiate-vars.sh
# RUN chmod +x /opt/flink/bin/initiate-vars.sh

###############################################
## Download Neccessary Jars to Flink Class Path
###############################################

## Iceberg Flink Library
RUN curl -L https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-flink-runtime-1.16/1.3.1/iceberg-flink-runtime-1.16-1.3.1.jar -o /opt/flink/lib/iceberg-flink-runtime-1.16-1.3.1.jar

## Hive Flink Library
RUN curl -L https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-hive-2.3.9_2.12/1.16.1/flink-sql-connector-hive-2.3.9_2.12-1.16.1.jar -o /opt/flink/lib/flink-sql-connector-hive-2.3.9_2.12-1.16.1.jar

## Hadoop Common Classes
RUN curl -L https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-common/2.8.3/hadoop-common-2.8.3.jar -o /opt/flink/lib/hadoop-common-2.8.3.jar

## Hadoop AWS Classes
RUN curl -L https://repo.maven.apache.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar -o /opt/flink/lib/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar 

## AWS Bundled Classes
RUN curl -L https://repo1.maven.org/maven2/software/amazon/awssdk/bundle/2.20.18/bundle-2.20.18.jar -o /opt/flink/lib/bundle-2.20.18.jar

## Icberg Nessie Library
# RUN curl -L https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-nessie/1.3.0/iceberg-nessie-1.3.0.jar -o /opt/flink/lib/iceberg-nessie-1.3.0.jar

## Install Nano to edit files
RUN apt update && apt install -y nano

CMD ["./bin/start-cluster.sh"]

## docker run --name flink-iceberg -p 8081:8081 -it alexmerced/flink-iceberg