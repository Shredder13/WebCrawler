import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WebCrawler {
	
	enum State {
		IDLE, RUNNING
	}

	public static final String ROBOTS = "robots.txt";

	public ArrayList<Pattern> blackList = new ArrayList<>();
	public ArrayList<Pattern> whiteList = new ArrayList<>();
	
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
	
	private HashSet<String> visitedUrls;
	
	private CrawlData crawlData;
	
	private String hostUrl;
	private String email = "";
	
	private WebCrawler() {
		downloadersPool = new ThreadPool(maxDownloaders);
		analyzersPool = new ThreadPool(maxAnalyzers);
		
		visitedUrls = new HashSet<>();
		opennedPorts = new ArrayList<>();
		
		crawlData = new CrawlData();
		
		downloadersPool.start();
		analyzersPool.start();
	}

	public static WebCrawler getInstance() {
		if (sInstance == null) {
			sInstance = new WebCrawler();
		}

		return sInstance;
	}
	
	public String getHostUrl() {
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

	public void setEmail(String email) {
		this.email = email;
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
					return name.matches("^.+_\\d+_\\d+\\.html");
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
				crawlData.put(CrawlData.OPENNED_PORTS, opennedPorts);
			} catch (PortScannerException e) {
				e.printStackTrace();
			}
		}

		if (disrespectRobotsTxt) {
			crawlData.put(CrawlData.DISRESPECT_ROBOTS_TXT, true);
		}
		handleRespectRobots();

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
			sendEmail();
		}
	}

	private void sendEmail() {
		// TODO: complete
	}
	
	private void buildStatisticsPage() {
		StatisticsPageBuilder pageBuilder = new StatisticsPageBuilder(crawlData);
		if (!pageBuilder.build()) {
			Log.d(String.format("Error creating statistics page for %s!", hostUrl));
		}
	}

	private void reset() {
		opennedPorts.clear();
		visitedUrls.clear();
		hostUrl = "";
		crawlData.clear();
		setState(State.IDLE);
	}

	/**
	 * adds matching regexes to the list of Patterns as a pattern
	 * @param match given matcher whose results will be added to list
	 * @param list given list to which the results will be added
	 */
	private void addPatternsToList(Matcher match, ArrayList<Pattern> list) {
		while (match.find()) {
			String url = match.group(1).trim();
			url = url.replace("/", "");
			url = this.getHostUrl() + url;
			url = url.replace("*", "+");
			url = url.replace("?", "\\?");
			list.add(Pattern.compile(url));
		}
	}

	private ArrayList<String> getRobotURLs(Matcher match) {
		ArrayList<String> urls = new ArrayList<>();
		while (match.find()) {
			String url = match.group(1).trim();
			url = url.replace("/", "");
			url = this.getHostUrl() + url;
			urls.add(url);

		}
		return urls;
	}

	/**
	 * gets the robots.txt file from the host and creates a while list and a black list
	 * when crawling it will be taken into account if the urls should be surfed to by the
	 * respect/disrespect robots user request
	 * respect/disrespect robots user request
	 */
	private void handleRespectRobots() {
		String allowReg = "Allow:\\s*(.+?\\s|.+)";
		String disallowReg = "Disallow:\\s*(.+?\\s|.+)";
		Pattern allowPattern = Pattern.compile(allowReg);
		Pattern disallowPattern = Pattern.compile(disallowReg);
		CrawlerHttpConnection con = new CrawlerHttpConnection(HTTP_METHOD.GET, this.getHostUrl() + ROBOTS,
				HTTP_VERSION.HTTP_1_0);
		String str = "";
		try {
			if (con.getResponse() != null) {
				str = con.getResponse().getBody();
			} else {
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Matcher allowMatch = allowPattern.matcher(str);
		Matcher disallowMatch = disallowPattern.matcher(str);
		if (!(boolean) crawlData.get(CrawlData.DISRESPECT_ROBOTS_TXT)) {
			addPatternsToList(allowMatch, whiteList);
			addPatternsToList(disallowMatch, blackList);
		} else {
			ArrayList<String> allowed = getRobotURLs(allowMatch);
			ArrayList<String> disallowed = getRobotURLs(disallowMatch);
			for (String s : allowed) {
				downloadersPool.submit(new DownloaderTask(s, DownloaderTask.RESOURCE_TYPE_HREF, downloadersPool, analyzersPool));
			}
			for (String s : disallowed) {
				downloadersPool.submit(new DownloaderTask(s, DownloaderTask.RESOURCE_TYPE_HREF, downloadersPool, analyzersPool));
			}
		}



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
