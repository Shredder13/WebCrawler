import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;


public class WebCrawler {
	
	enum State {
		IDLE, RUNNING
	}
	
	public static int maxDownloaders = 10;
	public static int maxAnalyzers = 2;
	public static ArrayList<String> imageExtensions = new ArrayList<>();
	public static ArrayList<String> videoExtensions = new ArrayList<>();
	public static ArrayList<String> documentExtensions = new ArrayList<>();
	
	private static WebCrawler sInstance;
	
	private State state = State.IDLE;
	private final Object stateLock = new Object();
	
	private ThreadPool downloadersPool;
	private ThreadPool analyzersPool;
	
	private ArrayList<Integer> opennedPorts;
	
	//private UniqueArrayList<String> visitedUrls;
	private HashSet<String> visitedUrls;
	
	private CrawlData crawlData;
	
	private String hostUrl;
	
	public static WebCrawler getInstance() {
		if (sInstance == null) {
			sInstance = new WebCrawler();
		}
		
		return sInstance;
	}
	
	private WebCrawler() {
		downloadersPool = new ThreadPool(maxDownloaders);
		analyzersPool = new ThreadPool(maxAnalyzers);
		
		visitedUrls = new HashSet<>();
		opennedPorts = new ArrayList<>();
		
		crawlData = new CrawlData();
		
		downloadersPool.start();
		analyzersPool.start();
	}
	
	public String getHost() {
		return hostUrl;
	}

	public State getState() {
		synchronized(stateLock) {
			return state;
		}
	}
	private void setState(State s) {
		synchronized(stateLock) {
			Log.d("Crawler state is " + s.toString());
			state = s;
		}
	}
	
	public CrawlData getCrawlData() {
		return crawlData;
	}
	
	public HashSet<String> getVisitedUrls() {
		return visitedUrls;
	}

	public void addVisitedURL(String url) {
		visitedUrls.add(url);
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
	
	private String getFixedHostUrl(String host) {
		StringBuilder result = new StringBuilder(host.replace("\\", "/"));
		if (!(host.startsWith("http://") || host.startsWith("https://"))) {
			result.insert(0, "http://");
		}
		if (!host.endsWith("/")) {
			result.append("/");
		}
		return result.toString();
	}
	
	public void start(String aHost, boolean portScan, boolean disrespectRobotsTxt) throws CrawlingException {
		Log.d(String.format("WebCrawler.start -> host = %s", aHost));
		String fixedHost = getFixedHostUrl(aHost);
		Log.d(String.format("WebCrawler.start -> fixed host = %s", fixedHost));
		try {
			HttpUrl hostUrlObj = new HttpUrl(fixedHost);
			
			InetAddress.getByName(hostUrlObj.getHost()).isReachable(1000);
			hostUrl = fixedHost;
		} catch (UnknownHostException e) {
			throw new CrawlingException("Host is unknown");
		} catch (IOException e) {
			throw new CrawlingException("Network error");
		}
		
		if (portScan) {
			try {
				PortScanner ps = new PortScanner(fixedHost);
				opennedPorts = ps.getOpennedPortsSync(1, 1024);
			} catch (PortScannerException e) {
				e.printStackTrace();
			}
		}
		
		if (disrespectRobotsTxt) {
			//TODO: ignore in the downloader
			crawlData.put(CrawlData.RESPECT_ROBOTS_TXT, true);
		}
		
		//start a downloader task for the host.
		Log.d("Starting main downloader");
		downloadersPool.submit(new DownloaderTask(fixedHost, DownloaderTask.RESOURCE_TYPE_HREF, downloadersPool, analyzersPool));
		
		setState(State.RUNNING); 
	}
	
	public void checkIfFinished() {
		if (AnalyzerTask.getNumOfAnalyzersAlive() == 0 && DownloaderTask.getNumOfDownloadersAlive() == 0) {
			buildStatisticsPage();
			reset();
			Log.d("Finished crawling!");
		}
	}
	
	private void buildStatisticsPage() {
		
	}

	private void reset() {
		opennedPorts.clear();
		visitedUrls.clear();
		hostUrl = "";
		crawlData.clear();
		setState(State.IDLE);
	}
	
	/*public static void main(String[] args) {
		WebCrawler crawler = WebCrawler.getInstance();
		try {
			crawler.start("127.0.0.1:8080/", false, false);
		} catch (CrawlingException e) {
			e.printStackTrace();
		}
	}*/
}
