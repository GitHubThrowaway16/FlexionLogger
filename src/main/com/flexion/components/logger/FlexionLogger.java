package main.com.flexion.components.logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class FlexionLogger {

	private static final SimpleDateFormat LOG_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HH_mm_ss");
	private static final SimpleDateFormat LOG_ENTRY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	/*
	 * Contains a queue of Strings FlexionLogger if logs are being received quicker than they can be written
	 */
	private ArrayBlockingQueue<String> mQueuedStrings;
	
	private FileWriter mFileWriter;	
	private String mLogFileFolder;
	private String mLineSeperator; //Line separators should be platform independent and loaded from System class
	private int mLogLifeTimeMillis; //The maximum time a log can live before a new log is created, loaded from logger.properties
	private long mLogFileStartMillis; //Record time when a log file is created, so we know when to rollover into a new log file
	private boolean mTracing; //If yes, messages are printed to monitor Logger performance
	
	private Thread mWriterThread;
	private Properties mProperties;
	
	public FlexionLogger() throws FlexionLoggerException {		
		readProperties();		
		mWriterThread = new Thread(new FlexionLoggerWriterThread(this));
		mWriterThread.start();
	}

	private void readProperties() throws FlexionLoggerException {
		mProperties = new Properties();
		FileInputStream in = null;
		String propertiesLocation = "resources" + System.getProperty("file.separator") + "logger.properties";
		try {
			in = new FileInputStream(propertiesLocation);
			mProperties.load(in);
		} catch (FileNotFoundException e) {
			throw new FlexionLoggerException("Properties file " + propertiesLocation + " expected but not found");
		} catch (IOException e) {
			throw new FlexionLoggerException("Properties file " + propertiesLocation + " cannot be accessed for read");
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}		
		/*
		 * The larger the log_queue_size, the more memory will be consumed holding messages waiting
		 * to be written to file, but the more capacity the program has to deal with a higher than
		 * expected frequency of logging
		 */
		int logQueueSize = Integer.parseInt(mProperties.getProperty("log_queue_size"));
		if (logQueueSize < 1) {
			throw new FlexionLoggerException("property log_queue_size must be 1 or greater");
		}
		mQueuedStrings = new ArrayBlockingQueue<String>(logQueueSize);
		mLogLifeTimeMillis = Integer.parseInt(mProperties.getProperty("log_lifetime_seconds")) * 1000;
		if (mLogLifeTimeMillis < 1000) {
			throw new FlexionLoggerException("property log_lifetime_seconds must be 1 or greater");
		}	
		
		mLogFileFolder = mProperties.getProperty("log_location");
		if (mLogFileFolder == null || mLogFileFolder.equals("")) {
			throw new FlexionLoggerException("property log_location must not be empty");
		}
		
		mTracing = "true".equalsIgnoreCase(mProperties.getProperty("tracing"));
		
		mLineSeperator = System.getProperty("line.separator");
	}

	/*
	 * Lightweight method to store the log message for processing in a queue
	 * synchronized to ensure the order of which the messages are sent is preserved in the log
	 */
	public synchronized void log(String logMsg) {		
		
		/*
		 * Appending a time stamp when it is received is more useful than at write time
		 * If there is a queue, logging the time stamp at write time would produce
		 * misleading timings in the log file
		 */
		StringBuffer stringBuffer = new StringBuffer("[").append(LOG_ENTRY_DATE_FORMAT.format(System.currentTimeMillis())).append("] ").append(logMsg).append(mLineSeperator);
		
		try {
			mQueuedStrings.put(stringBuffer.toString());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		/*
		 * Once we have successfully added a string to the queue, notify the WriterThread
		 */
		synchronized(mWriterThread) {
			mWriterThread.notify();
		}
	}

	/*
	 * Called from a separate thread (mWriterThread) to ensure receiving log messages is not slowed by writing to disk
	 */
	private void writeQueuedStringAndWait() {
		if (getQueuedStringsSize() > 0) {
			//If the life of the current log file exceeds the allowed life time for a log, start a new log file
			long logLifeMillis = (System.currentTimeMillis() - mLogFileStartMillis);
			if (logLifeMillis >= mLogLifeTimeMillis) {
				try {
					createNewLogFile();
				} catch (FlexionLoggerException e) {
					e.printStackTrace();
				}
			}
			
			try {
				mFileWriter.write(mQueuedStrings.take());
				mFileWriter.flush();
				if (mTracing) {
					System.out.println("Written Log, remaining String queue size: " + getQueuedStringsSize() + "\tLog file lifetime (milli): " + (System.currentTimeMillis() - mLogFileStartMillis));
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
		
		/*
		 * If there are no Strings in the queue, then wait (inside of a while loop to avoid spurious wake up)
		 */
		synchronized (mWriterThread) {
			while (getQueuedStringsSize() == 0) {
				try {
					mWriterThread.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private int getQueuedStringsSize() {
		return mQueuedStrings.size();
	}
	
	private void createNewLogFile() throws FlexionLoggerException {	
		stop();
		start();
	}
	
	public void stop() throws FlexionLoggerException {
		if (mFileWriter != null) {
			try {
				mFileWriter.close();
				mFileWriter = null;
			} catch (IOException e) {
				e.printStackTrace();
			}		
		}
		else {
			throw new FlexionLoggerException("Logger is already stopped");
		}
	}
	
	public void start() throws FlexionLoggerException {
		mLogFileStartMillis = System.currentTimeMillis();
		if (mFileWriter == null) {
			try {
				mFileWriter = new FileWriter(mLogFileFolder + LOG_FILE_DATE_FORMAT.format(System.currentTimeMillis()) + ".log");
			} catch (IOException e) {
				throw new FlexionLoggerException("Log file " + mLogFileFolder + LOG_FILE_DATE_FORMAT.format(System.currentTimeMillis()) + ".log" + " cannot be accessed for writing");
			}
		}
		else {
			throw new FlexionLoggerException("Logger is already started");
		}
	}

	public Properties getProperties() {
		return mProperties;
	}
	
	/*
	 * Thread that will check the FlexionLogger class
	 * for queued Strings and write them to a log file
	 */
	class FlexionLoggerWriterThread implements Runnable {

		private FlexionLogger mLogger;
		private long mMillisSinceLastLoop;

		public FlexionLoggerWriterThread(FlexionLogger logger) {			
			mLogger = logger;			
			mMillisSinceLastLoop = System.currentTimeMillis();
		}

		public void run() {	
			/*
			 * Run throughout the life time of the Logger, inside writeQueuedStringAndWait the logger will 
			 * wait() until notified() if there are no more queued strings to write, reducing CPU load 
			 */
			while (true) {	
				if (mTracing) {
					System.out.println("WriterThread time since last write (milli): " + (System.currentTimeMillis() - mMillisSinceLastLoop));
					mMillisSinceLastLoop = System.currentTimeMillis();
				}
				mLogger.writeQueuedStringAndWait();
			}			
		}
		
	}

}
