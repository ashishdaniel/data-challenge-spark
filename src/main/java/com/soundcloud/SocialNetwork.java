package com.soundcloud;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

public class SocialNetwork {

	private static Logger LOGGER = LoggerFactory.getLogger(SocialNetwork.class);

	public static void main(String[] args) throws Exception {
		
		
		int degree = Integer.parseInt(System.getProperty("degree", "2"));
		String inputFile = System.getProperty("input.file");
		String outputDir = System.getProperty("output.dir", "graph-result");
		if (null == inputFile || !new File(inputFile).exists()) {
			LOGGER.error("input.file - {} .... invalid...", inputFile);
			System.exit(1);
		}

		LOGGER.info("input.file={}, output.dir={}, degree={}", inputFile, outputDir, degree);
		FileUtils.deleteDirectory(new File(outputDir)); //Deleting previously generated result

		SparkConf conf = new SparkConf().setAppName("data-challenge").setMaster("local");
		JavaSparkContext context = new JavaSparkContext(conf);

		//Reading input file and creating an RDD
		JavaRDD<String> textFile = context.textFile(inputFile);
		
		
		JavaPairRDD<String, Iterable<String>> friendMapRDD = textFile
				.map(line -> line.split("\t")).map(arr -> Arrays
						.<Tuple2<String, String>> asList(new Tuple2<>(arr[0], arr[1]), new Tuple2<>(arr[1], arr[0])))
				.flatMap(l -> l).mapToPair(t -> t).groupByKey();

		//extracting the friend map from the RDD
		Map<String, Iterable<String>> friendMap = friendMapRDD.collectAsMap();
		
		//Function to aggregate result of friend of friend till nth degree
		Function<Tuple2<String, Set<String>>, List<String>> resultFn = t -> {
			List<String> result = t._2.stream().filter(s -> !s.equals(t._1)).sorted().collect(Collectors.toList());
			result.add(0, t._1);
			return result;
		};
		
		//For final result... Sorting based to first entry of list. 
		JavaRDD<List<String>> resultRDD = friendMapRDD
				.map(t -> new Tuple2<>(t._1, friendsTillNthDegree(friendMap, t._1, 2))).map(resultFn)
				.sortBy(l -> l.get(0), true, 1);

		
		//Creating an RDD for final output - tab separated.
		JavaRDD<String> tabSeparated = resultRDD.map(l -> l.stream().collect(Collectors.joining("\t")));
		
		
		tabSeparated.saveAsTextFile(outputDir);

		context.close();

	}
	

	/**
	 * Function to fetch friends till nth degree
	 * 
	 * @param friendMap
	 * @param name
	 * @param degree
	 * @return
	 */
	public static Set<String> friendsTillNthDegree(Map<String, Iterable<String>> friendMap, String name, int degree) {

		Set<String> set = new HashSet<>();
		set.add(name);
		if (degree > 1) {
			for (String conn : friendMap.get(name)) {
				set.addAll(friendsTillNthDegree(friendMap, conn, --degree));
			}
		}
		return set;
	}
}
