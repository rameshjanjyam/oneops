/*******************************************************************************
 *  
 *   Copyright 2015 Walmart, Inc.
 *  
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *  
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  
 *******************************************************************************/

package com.oneops.daq.jms;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.oneops.sensor.events.BasicEvent;
import com.oneops.sensor.events.PerfEvent;
import com.oneops.sensor.thresholds.Threshold;
import com.oneops.sensor.thresholds.ThresholdsDao;
import com.oneops.util.AMQConnectorURI;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.log4j.Logger;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

//import org.springframework.jms.connection.CachingConnectionFactory;

/**
 * The Class SensorPublisher.
 */
public class SensorPublisher {
	private static final Logger logger = Logger.getLogger(SensorPublisher.class);
	
    private String user = ActiveMQConnection.DEFAULT_USER;
    private String password = ActiveMQConnection.DEFAULT_PASSWORD;
    private String url = ActiveMQConnection.DEFAULT_BROKER_URL + "?connectionTimeout=1000";
    private String queueBase = "perf-in-q";
    private ConcurrentHashMap<Long,Long> manifestCache = new ConcurrentHashMap<>();
    
    private final AtomicLong eventCounter = new AtomicLong();
    private final AtomicLong missingManifestCounter = new AtomicLong();
    private final AtomicLong missingThresholdCounter = new AtomicLong();

    private CacheLoader<String, Threshold> loader = new CacheLoader<String, Threshold>() {
    	@Override
        public Threshold load(String key) throws Exception {
            String[] keyParts = key.split(":");
            Long manifestId = Long.parseLong(keyParts[0]);
            Threshold threshold = thresholdsDao.getThreshold(manifestId, keyParts[1]);
            logger.debug("loading: "+ manifestId.toString() +" "+ keyParts[1]);
            if (threshold.getThresholdJson().equals("n") && !threshold.isHeartbeat())
            	throw new NullThresholdException();
            return threshold;
          }
    };    
    
    private static int thresholdTTL = Integer.parseInt(System.getProperty("threshold_cache_ttl", "15"));
	private static String mqConnectionTimeout = System.getProperty("mqTimeout", "1000");  // timeout message send after 1 second
	private static String mqConnectionStartupRetries = System.getProperty("mqStartupRetries", "5");  // only reconnect 5 times on startup (to avoid publisher being stuck if MQ is down on startup
	private static int mqConnectionThreshold = Integer.parseInt(System.getProperty("mqRetryTimeout", "10000"));  // discard all the published messages for mqRetryTimeout milliseconds before attempting to send message again   
    private long lastFailureTimestamp = -1;
    
	private LoadingCache<String, Threshold> thresholdCache = CacheBuilder.newBuilder()
    	       .refreshAfterWrite(thresholdTTL, TimeUnit.MINUTES)
    	       .build(loader);
	// -Dpoolsize=n
    private static int poolsize = Integer.parseInt(System.getProperty("poolsize", "1")); 
    
    private JmsTemplate[] producers = new JmsTemplate[poolsize];
    
    private ThresholdsDao thresholdsDao = null;


	/**
	 * Sets the threshold dao.
	 *
	 * @param thresholdDao the new threshold dao
	 */
	public void setThresholdDao(ThresholdsDao thresholdDao) {
		this.thresholdsDao = thresholdDao;
	}

    private void showParameters() {
    	logger.info("Connecting to URL: " + url);
    	logger.info("Base queue name : " + queueBase);
    	logger.info("poolsize : " + poolsize);
    }

    /**
     * Inits the.
     *
     * @throws JMSException the jMS exception
     */
    public void init() throws JMSException {
		Properties properties = new Properties();				
		try {
			properties.load(this.getClass().getResourceAsStream ("/sink.properties"));
		} catch (IOException e) {
			logger.error("got: "+e.getMessage());
		}

		user = properties.getProperty("amq.user");
		password = System.getenv("KLOOPZ_AMQ_PASS");
				
		
		if (password == null) {
			throw new JMSException("missing KLOOPZ_AMQ_PASS env var");
		}

		AMQConnectorURI connectStringGenerator = new AMQConnectorURI();
		connectStringGenerator.setHost("opsmq");
		connectStringGenerator.setProtocol("tcp");
		connectStringGenerator.setPort(61616);
		connectStringGenerator.setTransport("failover");
		connectStringGenerator.setDnsResolve(true);
		connectStringGenerator.setKeepAlive(true);
		HashMap<String,String> transportOptions = new HashMap<>();
		transportOptions.put("initialReconnectDelay", "1000");
		transportOptions.put("startupMaxReconnectAttempts", mqConnectionStartupRetries);
		transportOptions.put("timeout", mqConnectionTimeout);
		transportOptions.put("useExponentialBackOff", "false");
		connectStringGenerator.setTransportOptions(transportOptions);
		url = connectStringGenerator.build();		
		
		showParameters();		

		// Create the connection.
		ActiveMQConnectionFactory amqConnectionFactory = new ActiveMQConnectionFactory(user, password, url);
		amqConnectionFactory.setUseAsyncSend(true);
		PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory(amqConnectionFactory);
		pooledConnectionFactory.setMaxConnections(4);
		pooledConnectionFactory.setIdleTimeout(10000);
				
		for (int i=0;  i < poolsize; i++) {
			JmsTemplate producerTemplate = new JmsTemplate(pooledConnectionFactory);
			producerTemplate.setSessionTransacted(false);
			int shard = i + 1;
			Destination perfin = new org.apache.activemq.command.ActiveMQQueue(queueBase +"-"+shard);
			producerTemplate.setDefaultDestination(perfin);
			producerTemplate.setDeliveryPersistent(false);
			producers[i] = producerTemplate;			
		}
		
		        
    }

	/**
	 * Enrich and publish.
	 *
	 * @param event the event
	 * @throws JMSException the jMS exception
	 * @throws ExecutionException 
	 */
	@SuppressWarnings("unused")
	public void enrichAndPublish(PerfEvent event) throws JMSException {
		
		if (eventCounter.incrementAndGet() % 100 == 0)
			logger.info("Publish event count: "+eventCounter.get() +
				" manifest miss: "+missingManifestCounter.get()+
				" threshold miss: "+missingThresholdCounter.get());		
				
    	Long manifestId;    	
    	if (manifestCache.containsKey(event.getCiId())) 
    		manifestId = manifestCache.get(event.getCiId());    	   
    	else {
    		manifestId = thresholdsDao.getManifestId(event.getCiId());
    		if (manifestId != null)
    			manifestCache.put(event.getCiId(),manifestId); 
    	}
    	if (manifestId == null) {
    		long missCount = missingManifestCounter.incrementAndGet();
    		logger.info("no publishMessage - manifestId==null for ciId: "+event.getCiId());
    		return;
    	}
    	logger.debug("manifestId: "+manifestId.toString());
        
    	String key = manifestId.toString()+":"+event.getSource();
    	try {
    		Threshold tr = thresholdCache.get(key);
    		logger.debug("treshold: "+tr.getSource() + " "+ tr.getThresholdJson());    	
    		event.setManifestId(manifestId);
    		event.setChecksum(tr.getCrc());  				
    	} 
    	catch (Exception e) {
    		logger.debug("no publishMessage - threshold==null for key:" + manifestId + "::" + event.getSource());
    		long missCount = missingThresholdCounter.incrementAndGet();
    		return;
    	} 
	    publishMessage(event);	
  
    }
	
	
    /**
     * Publish message.
     *
     * @param event the event
     * @throws JMSException the jMS exception
     */
    public void publishMessage(final BasicEvent event) throws JMSException {

		if (System.currentTimeMillis() > lastFailureTimestamp) {
			int shard = (int) (event.getManifestId() % poolsize);
			try {
				producers[shard].send(session -> {
                    ObjectMessage message = session.createObjectMessage(event);
                    message.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    message.setLongProperty("ciId", event.getCiId());
                    message.setLongProperty("manifestId", event.getManifestId());
                    message.setStringProperty("source", event.getSource());
					if (logger.isDebugEnabled()) {
						logger.debug("Published: ciId:" + event.getCiId() + "; source:" + event.getSource());
					}
                    return message;
                });
				lastFailureTimestamp = -1;
			} catch (JmsException exception) {
				logger.debug("There was an error sending a message. Discarding messages for " + mqConnectionThreshold + " ms");
				lastFailureTimestamp = System.currentTimeMillis() + mqConnectionThreshold;
			}
		}
	}
  
    
    /**
     * Cleanup.
     */
    public void cleanup() {
    	logger.info("Closing AMQ connection");
    	closeConnection();
    }
    
    /**
     * Close connection.
     */
    public void closeConnection() {
    	for (JmsTemplate jt : producers) {
			((PooledConnectionFactory)jt.getConnectionFactory()).stop();
    	}
    	producers = null;
    }
}
