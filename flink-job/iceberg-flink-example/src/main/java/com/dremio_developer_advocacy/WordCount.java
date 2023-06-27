package com.dremio_developer_advocacy;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class WordCount {

    // *************************************************************************
    // PROGRAM
    // *************************************************************************

    public static void main(String[] args) throws Exception {

        // Create the execution environment. This is the main entrypoint
        // to building a Flink application.
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Setup the data source
        DataStream<String> text = env.fromElements(
            "To be or not to be",
            "That is the question",
            "Whether 'tis nobler in the mind to suffer",
            "The slings and arrows of outrageous fortune",
            "Or to take arms against a sea of troubles",
            "And by opposing end them"
        );

        DataStream<Tuple2<String, Integer>> counts =
            // The text lines read from the source are split into words
            // using a user-defined function. The tokenizer, implemented below,
            // will output each word as a (2-tuple) containing (word, 1)
            text.flatMap(new Tokenizer())
                .name("tokenizer")
                // keyBy groups tuples based on the "0" field, the word.
                // Using a keyBy allows performing aggregations and other
                // stateful transformations over data on a per-key basis.
                // This is similar to a GROUP BY clause in a SQL query.
                .keyBy(value -> value.f0)
                // For each key, we perform a simple sum of the "1" field, the count.
                // If the input data stream is bounded, sum will output a final count for
                // each word. If it is unbounded, it will continuously output updates
                // each time it sees a new instance of each word in the stream.
                .sum(1)
                .name("counter");

        // Print the counts directly in the console
        counts.print().name("print-sink");

        // Apache Flink applications are composed lazily. Calling execute
        // submits the Job and begins processing.
        env.execute("WordCount");
    }

    // *************************************************************************
    // USER FUNCTIONS
    // *************************************************************************

    /**
     * Implements the string tokenizer that splits sentences into words as a user-defined
     * FlatMapFunction. The function takes a line (String) and splits it into multiple pairs in the
     * form of "(word,1)" ({@code Tuple2<String, Integer>}).
     */
    public static final class Tokenizer
            implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            // normalize and split the line
            String[] tokens = value.toLowerCase().split("\\W+");

            // emit the pairs
            for (String token : tokens) {
                if (token.length() > 0) {
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
    }
}