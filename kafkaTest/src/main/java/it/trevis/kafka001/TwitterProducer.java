package it.trevis.kafka001;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;

public class TwitterProducer {
	
	private static final Logger logger = LoggerFactory.getLogger(TwitterProducer.class);
	
	public TwitterProducer() {}
	
	public void run() {
		// code here
		BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(10000);
		Client client = createTwitterClient(msgQueue);
		client.connect();
		
		KafkaProducer<String,String> producer = createKafkaProducer();
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("Shutting down client from twitter..");
			client.stop();
			logger.info("Closing producetr..");
			// send all msg in memory to kafka before shutting down
			producer.close();
			logger.info("Shutting down done!");
		}));
		
		while(!client.isDone()) {
			String msg = null;
			try {
				msg = msgQueue.poll(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
				client.stop();
			}
			if(msg != null) {
				producer.send(new ProducerRecord<String, String>("twitter_tweets", msg), new Callback() {

					@Override
					public void onCompletion(RecordMetadata metadata, Exception e) {
						if(e != null) {
							logger.error("Errore", e);
						}
					}
				});
			}
		}
		
	}
	
	public KafkaProducer<String, String> createKafkaProducer(){
		String bootstrapServers = "127.0.0.1:9092";
		Properties properties = new Properties();
		properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		// for idempotence producer
		properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
		properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
		properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
		properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
		// for high throughput producer
		properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
		properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
		properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024));
		
		
		KafkaProducer<String,String> producer = new KafkaProducer<>(properties);
		return producer;
	}
	
	public Client createTwitterClient(BlockingQueue<String> msgQueue) {
		String consumerKey = "";
		String consumerSecret = "";
		String token = "";
		String secret = "";
		
		Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
		StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
		List<String> terms = Lists.newArrayList("bitcoin", "monero");
		hosebirdEndpoint.trackTerms(terms);
		
		// These secrets should be read from a config file
		Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);
		
		ClientBuilder builder = new ClientBuilder()
				  .name("Hosebird-Client-01")                              // optional: mainly for the logs
				  .hosts(hosebirdHosts)
				  .authentication(hosebirdAuth)
				  .endpoint(hosebirdEndpoint)
				  .processor(new StringDelimitedProcessor(msgQueue));                          // optional: use this if you want to process client events

				Client hosebirdClient = builder.build();
				return hosebirdClient;
				// Attempts to establish a connection.
				// hosebirdClient.connect();
		
	}

	public static void main(String[] args) {
		new TwitterProducer().run();
	}

}
