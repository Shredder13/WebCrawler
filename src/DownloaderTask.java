import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DownlaoderTask is a task that is account of downloading link content by sending
 * GET or HEAD methods.
 */
public class DownloaderTask extends Task {

	public static final int RESOURCE_TYPE_HREF = 0;
	public static final int RESOURCE_TYPE_IMG = 1;
	public static final int RESOURCE_TYPE_VIDEO = 2;
	public static final int RESOURCE_TYPE_DOC = 3;

	ThreadPool downloadersPool;
	ThreadPool analyzersPool;

	private int resourceType;
	private String url;

	WebCrawler webCrawler;
	
	CrawlerHttpConnection conn;
	CrawlerHttpConnection.Response response;

	private static int numDownloadersAlive = 0;
	private static final Object numDownloadersAliveLock = new Object();

	public DownloaderTask(String url, int resourceType, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
		this.resourceType = resourceType;
		this.url = url;
		webCrawler = WebCrawler.getInstance();

		//Increasing the number of active downloaders here, before the task is queued in the downloadersPool,
		//makes sure that the AnalyzerTask that triggered this task isn't finished yet and also the downloaders counter is not 0.
		//It actually eliminates the chance that that both downloadersPool and analyzersPool are empty at the 
		//same time when there's job to be done.
		increaseNumOfDownloadersAlive();
	}

	/**
	 * Perform the URL download using GET for href and HEAD for blobs.
	 */
	@Override
	public void run() {

		Log.d(String.format("Running downloader task --> url = %s", url));
		try {

			//check if external or internal link
			boolean internal = false;

			HttpUrl urlObj = new HttpUrl(url);
			HttpUrl origUrlObj = new HttpUrl(webCrawler.getHostUrl());
			internal = urlObj.getHost().equals(origUrlObj.getHost());
			
			//Here the crawling statistics is aggregated
			CrawlData cd = webCrawler.getCrawlData();

			//check against robots.txt
			if (checkLists(cd, url)) {
				throw new Exception("balacklisted by robots.txt");
			}

			//if visited, do not download. This is a HashSet --> contains is O(1).
			if (webCrawler.getVisitedUrls().contains(url)) {
				throw new Exception("URL is visited");
			} else {
				Log.d(String.format("Url not yet visited! processing further : url = %s", url));
				webCrawler.addVisitedURL(url);
			}
			
			//If it is an HREF link, we download it using the GET method and send it to an analyzer.
			//BONUS: if the response is "301 moved permanently" we redirect the crawl to that page. 
			if (resourceType == RESOURCE_TYPE_HREF) {
				Log.d(String.format("Resource is a HTML : url = %s", url));
				
				//Send GET request
				conn = new CrawlerHttpConnection(HTTP_METHOD.GET, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();

				if (response != null) {
					switch(response.getCode()) {
					case ERR_301_MOVED_PERMANENTLY:
						handle301Moved(response);
						break;
					default:
					case C200_OK:
						//Handle internal / external links
						if (internal) {
							Log.d(String.format("HTML is internal! send to analyzer : url = %s", url));
							cd.put(CrawlData.NUM_OF_INTERNAL_LINKS, (Long)cd.get(CrawlData.NUM_OF_INTERNAL_LINKS) + 1);
							
							//Send internal HREFs to an analyzer.
							analyzersPool.submit(new AnalyzerTask(url, response.getBody(), downloadersPool, analyzersPool));
						} else {
							cd.put(CrawlData.NUM_OF_EXTERNAL_LINKS, (Long)cd.get(CrawlData.NUM_OF_EXTERNAL_LINKS) + 1);
							((HashSet<String>) cd.get(CrawlData.CONNECTED_DOMAINS)).add(urlObj.getHost());
						}

						cd.put(CrawlData.NUM_OF_PAGES, (Long)cd.get(CrawlData.NUM_OF_PAGES) + 1);
						cd.put(CrawlData.SIZE_OF_PAGES, (Long)cd.get(CrawlData.SIZE_OF_PAGES) + Long.valueOf(response.getHeaders().get("content-length")));
						break;
					}
					
				}
				
			} else {
				//If its an image, video or document - we send a HEAD request.
				conn = new CrawlerHttpConnection(HTTP_METHOD.HEAD, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();
				if (response != null) {
					Log.d(String.format("Resource is a BLOB : url = %s", url));
					
					switch(response.getCode()) {
					case ERR_301_MOVED_PERMANENTLY:
						//BONUS: handle "301 moved permanently" for images
						handle301Moved(response);
						break;
					default:
					case C200_OK:
						//Fill in the crawling statistics
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
						break;
					}
				}
			}

			if (response != null) {
				webCrawler.getCrawlData().updateAvgRTT(response.getRTT());
			}
		} catch (Exception e) {
			Log.d(e.getMessage());
		} finally {
			if (conn != null) {
				conn.close();
			}
			decreaseNumOfDownloadersAlive();
		}
	}
	
	/**
	 * Handle the case when receiveing 301 MOVED PERMANENTLY when requesting a resource.
	 * @param response the response object for the request
	 * @throws MalformedURLException if the move URL is incorrect.
	 */
	private void handle301Moved(CrawlerHttpConnection.Response response) throws MalformedURLException {
		String movedUrl = response.getHeaders().get("location");
		Log.d("Moved permanently to " + movedUrl);
		if (movedUrl != null) {
			String newUrl;
			if (movedUrl.matches("^https?:\\/\\/.+")) {
				//If the link starts with "http(s)"
				newUrl = movedUrl;
			} else {
				//If the link starts with a local path we just change it
				HttpUrl movedUrlObj = new HttpUrl(url);
				movedUrlObj.setFile(movedUrl);
				newUrl = movedUrlObj.toString();
			}
			
			downloadersPool.submit(new DownloaderTask(newUrl, resourceType, downloadersPool, analyzersPool));
		}
	}

	@Override
	protected void shutdown() throws IOException {
		if (conn != null) {
			conn.close();
		}
	}

	private void increaseNumOfDownloadersAlive() {
		synchronized (numDownloadersAliveLock) {
			numDownloadersAlive++;
		}
	}
	private void decreaseNumOfDownloadersAlive() {
		synchronized (numDownloadersAliveLock) {
			numDownloadersAlive--;
			Log.d("remaining items in Downloaders queue: " + numDownloadersAlive);
		}
		
		webCrawler.checkIfFinished();
	}

	public static int getNumOfDownloadersAlive() {
		synchronized (numDownloadersAliveLock) {
			return numDownloadersAlive;
		}
	}

	/**
	 * checks if we need to respect the robots.txt
	 * @param cd the crawl data for this crawl
	 * @param url url to be checked if should be crawled
	 * @return true if we shouldn't crawl to the given url, false otherwise
     */
	private boolean checkLists(CrawlData cd, String url) {
		// check in black and white lists according to dis/respect robots
		if (!(boolean) cd.get(CrawlData.DISRESPECT_ROBOTS_TXT)) {
			// check if in whitelist and whould be crawled
			for (Pattern p : webCrawler.whiteList) {
				Matcher matcher = p.matcher(url);
				if (matcher.matches()) {
					Log.d("Should crawl into " + url + " (Allow in robots.txt).");
					return false;
				}
			}
			// not on whitelist, check if in blacklist- shouldn't be crawled
			for (Pattern p : webCrawler.blackList) {
				if (url.startsWith(p.toString())) {
					Log.d("Should not crawl into " + url + " (robots.txt).");
					return true;
				}
			}
		}
		return false;
	}
}
