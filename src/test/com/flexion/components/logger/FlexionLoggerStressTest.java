package test.com.flexion.components.logger;

import main.com.flexion.components.logger.FlexionLogger;
import main.com.flexion.components.logger.FlexionLoggerException;

public class FlexionLoggerStressTest {	
	/*
	 * Harness to stress test FlexionLogger
	 */
	public static void main(String args[]) {
		
		FlexionLogger logger = null;
		try {
			logger = new FlexionLogger();
			logger.start();

			/*
			 * Simulation of large amounts of logging by starting 20 threads,
			 * each will write a set number of log messages to the log file at 
			 * random intervals
			*/
			for (int i = 0; i < 20; i++) {
				Thread moduleThread = new Thread(new FlexionComponentMock(logger, i));
				moduleThread.start();
			}
		} catch (FlexionLoggerException e) {
			e.printStackTrace();
		}
		
	}
}
