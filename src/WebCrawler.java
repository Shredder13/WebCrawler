import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * The WebCrawler is a singleton class that is in charge of executing the crawling mission, end-to-end. 
 *
 */
public class WebCrawler {
	
	enum State {
		IDLE, RUNNING
	}

	public static final String ROBOTS = "robots.txt";

	//For robots.txt "disallow" and "allow"
	public ArrayList<Pattern> blackList = new ArrayList<>();
	public ArrayList<Pattern> whiteList = new ArrayList<>();
	
	//Data from the config.ini
	public static int maxDownloaders = 10;
	public static int maxAnalyzers = 2;
	public static ArrayList<String> imageExtensions = new ArrayList<>();
	public static ArrayList<String> videoExtensions = new ArrayList<>();
	public static ArrayList<String> documentExtensions = new ArrayList<>();
	
	private static WebCrawler sInstance;
	
	private State state = State.IDLE;
	private final Object stateLock = new Object();
	
	//The downlaoders & analyzers. Each one of them is a threadpool, with max-threads property.
	//They send tasks to each other in the downloader-analyzer loop.
	private ThreadPool downloadersPool;
	private ThreadPool analyzersPool;
	
	private ArrayList<Integer> opennedPorts;
	
	private HashSet<String> visitedUrls;
	
	private CrawlData crawlData;
	
	private String hostUrl;
	private String email = "";
	
	/**
	 * Creates the WebCrawler instance. Starting the downloaders & analyzer pools.
	 */
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
	
	/**
	 * @return the crawled domain
	 */
	public String getHostUrl() {
		return hostUrl;
	}

	/**
	 * @return the crawler state, one of <code>IDLE, RUNNING</code>
	 */
	public State getState() {
		synchronized(stateLock) {
			return state;
		}
	}
	
	/**
	 * Sets the crawler state, one of <code>IDLE, RUNNING</code>
	 * @param s - the state.
	 */
	private void setState(State s) {
		synchronized(stateLock) {
			Log.d("Crawler state is " + s.toString());
			state = s;
		}
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
	/**
	 * @return the statistics object.
	 */
	public CrawlData getCrawlData() {
		return crawlData;
	}
	
	public HashSet<String> getVisitedUrls() {
		return visitedUrls;
	}

	public void addVisitedURL(String url) {
		visitedUrls.add(url);
	}
	
	/**
	 * @return a list of past crawled domains.
	 */
	public ArrayList<String> getCrawlingHistory() {
		ArrayList<String> result = new ArrayList<>();
		
		try {
			String root = WebServer.root;
			File rootFolder = new File(root);
			
			//List files with a certain name pattern (of statistics pages).
			File[] files = rootFolder.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					//list the file only if the name matches the pattern
					return name.matches("^.+_\\d+_\\d+\\.html");
				}
			});	
			
			//Convert the array into a list
			if (files != null) {
				for (File f : files) {
					result.add(f.getName());
				}
			}
			
			//Sort the list for the good sake.
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
	
	/**
	 * modifying the given <code>host</code> for crawling. Adding "http://" for example.
	 * @param host
	 * @return
	 */
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
	
	/**
	 * Starts the crawling process. If requested a port scan is made, and also handling robots.txt as requested.
	 * 
	 * @param aHost - the given host to crawl on.
	 * @param portScan - if to perform a port scan
	 * @param disrespectRobotsTxt
	 * @throws CrawlingException if there's an error accessing the given host.
	 */
	public void start(String aHost, boolean portScan, boolean disrespectRobotsTxt) throws CrawlingException {
		Log.d(String.format("WebCrawler.start -> host = %s", aHost));
		
		//complete the host url
		String fixedHost = getFixedHostUrl(aHost);
		
		Log.d(String.format("WebCrawler.start -> fixed host = %s", fixedHost));
		
		try {
			//Make sure that the host is reachable.
			HttpUrl hostUrlObj = new HttpUrl(fixedHost);
			InetAddress.getByName(hostUrlObj.getHost()).isReachable(1000);
			hostUrl = fixedHost;
		} catch (UnknownHostException e) {
			throw new CrawlingException("Host is unknown");
		} catch (IOException e) {
			throw new CrawlingException("Network error");
		}
		
		if (portScan) {
			//Starts the multi-threaded port scan
			try {
				PortScanner ps = new PortScanner(fixedHost);
				opennedPorts = ps.getOpennedPortsSync(1, 1024);
				crawlData.put(CrawlData.OPENNED_PORTS, opennedPorts);
			} catch (PortScannerException e) {
				e.printStackTrace();
			}
		}
		
		//handle robots.txt
		if (disrespectRobotsTxt) {
			crawlData.put(CrawlData.DISRESPECT_ROBOTS_TXT, true);
		}
		handleRespectRobots();

		//start a downloader task for the host.
		Log.d("Starting main downloader");
		downloadersPool.submit(new DownloaderTask(fixedHost, DownloaderTask.RESOURCE_TYPE_HREF, downloadersPool, analyzersPool));
		
		setState(State.RUNNING); 
	}
	
	/**
	 * Each time a downloader or analyzer task is finished, it checks if the process is done.
	 * When it is, th statistics page is built and the crawler resets.
	 */
	public synchronized void checkIfFinished() {
		if (state.equals(State.RUNNING) && AnalyzerTask.getNumOfAnalyzersAlive() == 0 && DownloaderTask.getNumOfDownloadersAlive() == 0) {
			buildStatisticsPage();
			Log.d("Finished crawling! Sending email to " + email);
			sendEmail();
			reset();
		}
	}

	private void sendEmail() {
		if (email != null && email.length() > 0) {
			String to = email;
			String from = "nbtklab2@gmail.com";
			final String username = "nbtklab2";
			final String password = "!1Password1!";

			// Assuming you are sending email through relay.jangosmtp.net
			String host = "smtp.gmail.com";

			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port", "587");

			// Get the Session object.
			Session session = Session.getInstance(props, new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});

			try {
				Message message = new MimeMessage(session);
				message.setFrom(new InternetAddress(from));
				message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
				message.setSubject("Crawl have finished!");
				message.setText("Crawl to " + getHostUrl() + " have finished!");

				// Send message
				Transport.send(message);

				Log.d("Email sent successfully.");

			} catch (MessagingException e) {
				Log.d(e.getMessage());
			}
		} else {
			Log.d("No email address was specified!");
		}
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
		Log.d("Crawler is ready for another round!");
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
}
