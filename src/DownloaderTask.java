import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloaderTask extends Task {

	public static final int RESOURCE_TYPE_HREF = 0;
	public static final int RESOURCE_TYPE_IMG = 1;
	public static final int RESOURCE_TYPE_VIDEO = 2;
	public static final int RESOURCE_TYPE_DOC = 3;

	public static final String robots = "robots.txt";
	
	ThreadPool downloadersPool;
	ThreadPool analyzersPool;
	
	private int resourceType;
	private String url;
	
	WebCrawler webCrawler;
	
	private static int numDownloadersAlive = 0;
	private static final Object numDownloadersAliveLock = new Object();
	
	public DownloaderTask(String url, int resourceType, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
		this.resourceType = resourceType;
		this.url = url;
		webCrawler = WebCrawler.getInstance();
		
		increaseNumOfAnalyzersAlive();
	}

	@Override
	public void run() {
		
		Log.d(String.format("Running downloader task --> url = %s", url));
		try {
			
			//check if external or internal link
			boolean internal = false;

			HttpUrl urlObj = new HttpUrl(url);
			HttpUrl origUrlObj = new HttpUrl(webCrawler.getHostUrl());
			internal = urlObj.getHost().equals(origUrlObj.getHost());

			//if visited, do not download. This is a HashSet --> contains is O(1).
			if (webCrawler.getVisitedUrls().contains(url)) {
				return;
			} else {
				Log.d(String.format("Url not yet visited! processing further : url = %s", url));
				webCrawler.addVisitedURL(url);
			}
			
			CrawlerHttpConnection conn;
			CrawlerHttpConnection.Response response;
			CrawlData cd = webCrawler.getCrawlData();

			//handleRespectRobots(cd);
			
			long startMillis, endMillis;
			
			if (resourceType == RESOURCE_TYPE_HREF) {
				conn = new CrawlerHttpConnection(HTTP_METHOD.GET, url, HTTP_VERSION.HTTP_1_0);
				startMillis = System.currentTimeMillis();
				response = conn.getResponse();
				conn.close();
				endMillis = System.currentTimeMillis();
				
				//TODO: Remove the C200_OK part when doing the 302 bonus!
				if (response != null && response.getCode() == HTTP_CODE.C200_OK) {
					Log.d(String.format("Resource is a HTML : url = %s", url));
					if (internal) {
						Log.d(String.format("HTML is internal! send to analyzer : url = %s", url));
						cd.put(CrawlData.NUM_OF_INTERNAL_LINKS, (Long)cd.get(CrawlData.NUM_OF_INTERNAL_LINKS) + 1);
						analyzersPool.submit(new AnalyzerTask(url, response.getBody(), downloadersPool, analyzersPool));
					} else {
						cd.put(CrawlData.NUM_OF_EXTERNAL_LINKS, (Long)cd.get(CrawlData.NUM_OF_EXTERNAL_LINKS) + 1);
						((HashSet<String>) cd.get(CrawlData.CONNECTED_DOMAINS)).add(urlObj.getHost());
						//TODO: WTF is "link of crawled domains"...?
					}
					
					cd.put(CrawlData.NUM_OF_PAGES, (Long)cd.get(CrawlData.NUM_OF_PAGES) + 1);
					cd.put(CrawlData.SIZE_OF_PAGES, (Long)cd.get(CrawlData.SIZE_OF_PAGES) + Long.valueOf(response.getHeaders().get("content-length")));
				}
			} else {
				
				conn = new CrawlerHttpConnection(HTTP_METHOD.HEAD, url, HTTP_VERSION.HTTP_1_0);
				startMillis = System.currentTimeMillis();
				response = conn.getResponse();
				conn.close();
				endMillis = System.currentTimeMillis();
				
				if (response != null && response.getCode() == HTTP_CODE.C200_OK) {
					Log.d(String.format("Resource is a BLOB : url = %s", url));
					
					if (response.getHeaders().containsKey("content-length")) {
						switch(resourceType) {
						case RESOURCE_TYPE_IMG:
							cd.put(CrawlData.NUM_OF_IMAGES, (Long)cd.get(CrawlData.NUM_OF_IMAGES) + 1);
							cd.put(CrawlData.SIZE_OF_IMAGES, (Long)cd.get(CrawlData.SIZE_OF_IMAGES) + Long.valueOf(response.getHeaders().get("content-length")));
							break;
						case RESOURCE_TYPE_VIDEO:
							cd.put(CrawlData.NUM_OF_VIDEOS, (Long)cd.get(CrawlData.NUM_OF_VIDEOS) + 1);
							cd.put(CrawlData.SIZE_OF_VIDEOS, (Long)cd.get(CrawlData.SIZE_OF_VIDEOS) + Long.valueOf(response.getHeaders().get("content-length")));
							break;
						case RESOURCE_TYPE_DOC:
							cd.put(CrawlData.NUM_OF_DOCUMENTS, (Long)cd.get(CrawlData.NUM_OF_DOCUMENTS) + 1);
							cd.put(CrawlData.SIZE_OF_DOCUMENTS, (Long)cd.get(CrawlData.SIZE_OF_DOCUMENTS) + Long.valueOf(response.getHeaders().get("content-length")));
							break;
						}
					}
				}
			}
			
			webCrawler.getCrawlData().updateAvgRTT(endMillis - startMillis);
			
		} catch (MalformedURLException e) {
			//Broken link
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			decreaseNumOfAnalyzersAlive();
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

	private void increaseNumOfAnalyzersAlive() {
		synchronized (numDownloadersAliveLock) {
			numDownloadersAlive++;
		}
	}
	private void decreaseNumOfAnalyzersAlive() {
		synchronized (numDownloadersAliveLock) {
			numDownloadersAlive--;
			webCrawler.checkIfFinished();
		}
	}

	private void handleRespectRobots(CrawlData cd) {
		String regex = "";
		CrawlerHttpConnection con = new CrawlerHttpConnection(HTTP_METHOD.GET, webCrawler.getHostUrl() + robots,
				HTTP_VERSION.HTTP_1_0);
		if ((boolean) cd.get(CrawlData.RESPECT_ROBOTS_TXT)) {
			// surf only to allowed links, add black list of URLS
			regex = "allow:\\s*(.+?\\s|.+)";
		} else {
			String disReg = "disallow:\\s*(.+?\\s|.+)";
		}
		Pattern pattern = Pattern.compile(regex);
		String str = "";
		try {
			if (con.getResponse() != null) {
				str = con.getResponse().getBody();
				str = str.toLowerCase();
			} else {
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Matcher match = pattern.matcher(str);
		while (match.find()) {
			String url = match.group(1).trim();
			downloadersPool.submit(new DownloaderTask(webCrawler.getHostUrl() + url, DownloaderTask.RESOURCE_TYPE_HREF,
					downloadersPool, analyzersPool));
		}
	}

	public static int getNumOfDownloadersAlive() {
		synchronized (numDownloadersAliveLock) {
			return numDownloadersAlive;
		}
	}
}
