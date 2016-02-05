import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
			CrawlData cd = webCrawler.getCrawlData();

			if (checkLists(cd, url)) {
				return;
			}

			//if visited, do not download. This is a HashSet --> contains is O(1).
			if (webCrawler.getVisitedUrls().contains(url)) {
				return;
			} else {
				Log.d(String.format("Url not yet visited! processing further : url = %s", url));
				webCrawler.addVisitedURL(url);
			}

			CrawlerHttpConnection conn;
			CrawlerHttpConnection.Response response;



			if (resourceType == RESOURCE_TYPE_HREF) {
				conn = new CrawlerHttpConnection(HTTP_METHOD.GET, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();

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
				response = conn.getResponse();
				conn.close();

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

			if (response != null) {
				webCrawler.getCrawlData().updateAvgRTT(response.getRTT());
			}
		} catch (Exception e) {
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
