import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;


public class WebCrawler {
	
	enum State {
		IDLE, RUNNING
	}
	
	public static int maxDownloaders = 10;
	public static int maxAnalyzers = 2;
	public static ArrayList<String> imageExtensions;
	public static ArrayList<String> videoExtensions;
	public static ArrayList<String> documentExtensions;
	
	private static WebCrawler sInstance;
	
	private State state = State.IDLE;
	private final Object stateLock = new Object();
	
	private ThreadPool downloadersPool;
	private ThreadPool analyzersPool;
	
	private ArrayList<Integer> opennedPorts;
	
	//private UniqueArrayList<String> visitedUrls;
	private HashSet<String> visitedUrls;
	
	private CrawlData crawlData;
	
	private String host;
	
	public static WebCrawler getInstance() {
		if (sInstance == null) {
			sInstance = new WebCrawler();
		}
		
		return sInstance;
	}
	
	public WebCrawler() {
		downloadersPool = new ThreadPool(maxDownloaders);
		analyzersPool = new ThreadPool(maxAnalyzers);
		
		visitedUrls = new HashSet<>();
		opennedPorts = new ArrayList<>();
		
		crawlData = new CrawlData();
		
		downloadersPool.start();
		analyzersPool.start();
	}
	
	public String getHost() {
		return host;
	}

	public State getState() {
		synchronized(stateLock) {
			return state;
		}
	}
	private void setState(State s) {
		synchronized(stateLock) {
			state = s;
		}
	}
	
	public CrawlData getCrawlData() {
		return crawlData;
	}
	
	public HashSet<String> getVisitedUrls() {
		return visitedUrls;
	}
	
	public ArrayList<String> getCrawlingHistory() {
		ArrayList<String> result = new ArrayList<>();
		
		try{
			String root = WebServer.root;
			File rootFolder = new File(root);
			File[] files = rootFolder.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return name.matches("^\\w+_\\d+_\\d+\\.html");
				}
			});	
			
			if (files != null) {
				for (File f : files) {
					result.add(f.getName());
				}
			}
			
			Collections.sort(result, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					return o1.compareTo(o2);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public void start(String aHost, boolean portScan, boolean disrespectRobotsTxt) throws CrawlingException {
		
		try {
			InetAddress.getByName(aHost).isReachable(1000);
			host = aHost;
		} catch (UnknownHostException e) {
			throw new CrawlingException("Host is unknown");
		} catch (IOException e) {
			throw new CrawlingException("Network error");
		}
		
		if (portScan) {
			try {
				PortScanner ps = new PortScanner(aHost);
				opennedPorts = ps.getOpennedPortsSync(1, 1024);
			} catch (PortScannerException e) {
				e.printStackTrace();
			}
		}
		
		if (disrespectRobotsTxt) {
			//TODO: ignore in the downloader
		}
		
		//TODO: start a downloader task for the host.
		downloadersPool.submit(new DownloaderTask());
		
		setState(State.RUNNING);
		
		//TODO: find a way to terminate the program.
		//We need to determine when the analyzers & downloaders are empty.
		//This should be done with callbacks or whatever. 
	}
}
