package test.com.flexion.components.logger;

import java.util.Random;

import main.com.flexion.components.logger.FlexionLogger;
import main.com.flexion.components.logger.FlexionLoggerException;

public class FlexionComponentMock implements Runnable {
	
	private int mTestLogsPerThread;
	private int mMaxWaitBetweenTestLogsMillis;
	private int mMessagesSent;
	private String mMessageToSend;
	private Random mRandom;
	private FlexionLogger mLogger;

	public FlexionComponentMock(FlexionLogger logger, int modNum) throws FlexionLoggerException {
		
		mLogger = logger;
		mMessagesSent = 0;
		mMessageToSend = "Thread " + modNum + " msg";
		mRandom = new Random();
		
		/*
		 * Load these properties once
		 */
		mTestLogsPerThread = Integer.parseInt(mLogger.getProperties().getProperty("test_logs_per_thread"));
		if (mTestLogsPerThread < 0) {
			throw new FlexionLoggerException("property test_logs_per_thread must be 0 or greater");
		}
		
		mMaxWaitBetweenTestLogsMillis = Integer.parseInt(mLogger.getProperties().getProperty("max_wait_between_test_logs_millis"));
		if (mMaxWaitBetweenTestLogsMillis < 1) {
			throw new FlexionLoggerException("property max_wait_between_test_logs_millis must be 1 or greater");
		}
		
	}

	public void run() {		
		while (mMessagesSent < mTestLogsPerThread) {
			mLogger.log(mMessageToSend);
			mMessagesSent++;
			
			/*
			 * Sleep for for a random amount of time up to value specified in logger.properties 
			 */
			try {
				Thread.sleep(mRandom.nextInt(mMaxWaitBetweenTestLogsMillis));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}			
		}		
	}	
	
}