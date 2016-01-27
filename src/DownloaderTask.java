import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

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
			
			URL urlObj = new URL(url);
			URL origUrlObj = new URL(webCrawler.getHost());
			internal = urlObj.getHost().equals(origUrlObj.getHost());
			
			//if visited, do not download. This is a HashSet --> contains is O(1).
			if (webCrawler.getVisitedUrls().contains(url)) {
				return;
			} else {
				Log.d(String.format("Url not yet visited! processing further : url = %s", url));
				webCrawler.getVisitedUrls().add(url);
			}
			
			//TODO: robots.txt handling
			
			CrawlerHttpConnection conn;
			CrawlerHttpConnection.Response response;
			
			switch(resourceType) {
			case RESOURCE_TYPE_IMG:
			case RESOURCE_TYPE_VIDEO:
			case RESOURCE_TYPE_DOC:
				//TODO: Just send HEAD request
				conn = new CrawlerHttpConnection(HTTP_METHOD.HEAD, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();
				
				Log.d(String.format("Resource is a BLOB : url = %s", url));
				
				//TODO: update BLOB crawl data.
				//webCrawler.getCrawlData().put(key, value);
				break;
			default:
			case RESOURCE_TYPE_HREF:
				conn = new CrawlerHttpConnection(HTTP_METHOD.GET, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();
				
				Log.d(String.format("Resource is a HTML : url = %s", url));
				
				if (internal) {
					Log.d(String.format("HTML is internal! send to analyzer : url = %s", url));
					analyzersPool.submit(new AnalyzerTask(url, response.getBody(), downloadersPool, analyzersPool));
				}
				
				//TODO: update HTML crawl data.
				//webCrawler.getCrawlData().put(key, value);
				break;
			}
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
	public static int getNumOfDownloadersAlive() {
		synchronized (numDownloadersAliveLock) {
			return numDownloadersAlive;
		}
	}
}
