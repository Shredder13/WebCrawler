import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * This class is in charge of setting up the web server and run it  
 *
 */
public class WebServer {

	private ThreadPool pool;
	
	private int maxThreads = 1;
	public static int port = 8080;
	public static String root = "C:/serverroot/";
	public static String defaultPage = "index.html";
	
	public static final int SOCKET_TIMEOUT_MILLIS = 30*1000;
	public final static String CRLF = "\r\n";
	
	private ServerSocket mServerSocket;
	private boolean stopListening = false;

	/**
	 * Reads the configuration file and starts the thread pool.
	 * @param configFilePath The configuration file path
	 * @throws NumberFormatException Is thrown if the configuration file holds wrong values for certain fields 
	 * @throws IOException Is thrown if there's an error reading the file
	 */
	public WebServer(String configFilePath) throws NumberFormatException, IOException {
		parseConfigFile(configFilePath);
		startThreadPool();
	}
	
	/**
	 * Initizlising a <code>ServerSocket</code>, accepting new connections until the web server is closed
	 * @throws IOException
	 */
	private void startListening() throws IOException {
		
		try {
			Log.d(String.format("Server is listening on port %d", port));
			
			mServerSocket = new ServerSocket(port);
			
			while (!stopListening) {
				Socket socket = mServerSocket.accept();
				Log.d("New connection accepted! Submitting to queue");
				pool.submit(new ListenTask(socket));
			}
			
			Log.d("Server finished listening successfully. Shutting down...");
			shutdown();
		} catch (IOException e) {
			Log.d("Error while listening!");
			//e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * Shutdown the server. Kill the SocketServer & thread pool.
	 */
	private void shutdown() {
		stopListening = true;
		try {
			if (mServerSocket != null) {
				// This case is possible because the constructor may throw exception
				mServerSocket.close();
			}
		} catch (IOException e) {
			Log.d("WebServer.shutdown() : cannot close server socket!");
			//e.printStackTrace();
		}
		if (pool != null) {
			pool.shutDown();
		}
		Log.d("Server is shutting down");
	}

	/**
	 * Creates a new ThreadPool instance and start it
	 */
	private void startThreadPool() {
		Log.d("Starting thread pool");
		pool = new ThreadPool(maxThreads);
		// creates the threads and runs them
		pool.start();
	}

	/**
	 * Parses the configuration file
	 * @param path The config file path
	 * @throws NumberFormatException Is thrown if a numeric field gets non-numeric value
	 * @throws IOException Is thrown if there's problem reading the config file.
	 */
	private void parseConfigFile(String path) throws NumberFormatException, IOException {
		//TODO: check what to do with LAB1 configurations - should we keep them?
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new FileReader(path));
			String line = "";
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split("=");
				
				if (parts.length == 2) {
					String key = parts[0];
					String val = parts[1];
					
					if (key.equals("port")) {
						port = Integer.valueOf(val);
					} else if (key.equals("root")) {
						root = val.replace('\\', '/');
					} else if (key.equals("defaultPage")) {
						defaultPage = val;
					} else if(key.equals("maxThreads")) {
						maxThreads = Integer.valueOf(val);
					} else if (key.equals("maxDownloaders")) {
						WebCrawler.maxDownloaders = Integer.valueOf(val);
					} else if (key.equals("maxAnalyzers")) {
						WebCrawler.maxAnalyzers = Integer.valueOf(val);
					} else if (key.equals("imageExtensions")) {
						WebCrawler.imageExtensions = extStringToList(val);
					} else if (key.equals("videoExtensions")) {
						WebCrawler.videoExtensions = extStringToList(val);
					} else if (key.equals("documentExtensions")) {
						WebCrawler.documentExtensions = extStringToList(val);
					}
				}
			}
		} catch (Exception e) {
			Log.d("Error in reading or parsing config file");
			//e.printStackTrace();
			throw e;
		} finally {
			if (reader != null) { 
				reader.close();
			}
		}
	}
	
	private ArrayList<String> extStringToList(String exts) {
		ArrayList<String> result = new ArrayList<>();
		
		String[] extSplit = exts.split(",");
		for (int i = 0; i < extSplit.length; i++) {
			result.add(extSplit[i]);
		}
		
		return result;
	}

	/**
	 * Entry point for the program - Create the web server.
	 * @param args
	 */
	public static void main(String[] args) {

		//Since the constructor throws exception, it might not complete.
		WebServer webServer = null;
		
		try {
			Log.d("Application started");
			webServer = new WebServer(root + "config.ini");
			webServer.startListening();
		} catch (NumberFormatException | IOException e) {
			//e.printStackTrace();
			if (webServer != null) {
				webServer.shutdown();
			}
		} finally {
			Log.d("Application exit");
		}
	}
}
