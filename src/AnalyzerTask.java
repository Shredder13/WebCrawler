import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A task for parsing HTML content, and extracting links, images, videos & documents,
 * and passing them to a downloader. 
 */
public class AnalyzerTask extends Task {
	
	private String currUrl;
	private String currHtml;
	private WebCrawler webCrawler;
	private ThreadPool downloadersPool;
	private ThreadPool analyzersPool;
	
	private static int numAnalyzersAlive = 0;
	private static final Object numAnalyzersAliveLock = new Object();
	
	public AnalyzerTask(String url, String html, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		currUrl = url;
		currHtml = html;
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
		
		webCrawler = WebCrawler.getInstance();
		
		//Increasing the number of active analyzers here, before the task is queued in the analyzersPool,
		//makes sure that the DownloadTask that triggered this task isn't finished yet and also the analyzers counter is not 0.
		//It actually eliminates the chance that that both downloadersPool and analyzersPool are empty at the 
		//same time when there's job to be done.
		increaseNumOfAnalyzersAlive();
	}

	/***
	 * Parse HTML file and extract the relevant content, and send it to be downloaded.
	 */
	@Override
	public void run() {
		Log.d(String.format("AnalyzerTask is running on %s", currUrl));
		HashMap<String, ArrayList<String>> exts = new HashMap<>();
		
		//Put the extensions from config.ini in a hash-map, for passing it to the HtmlParser.
		exts.put("imageExtensions", WebCrawler.imageExtensions);
		exts.put("videoExtensions", WebCrawler.videoExtensions);
		exts.put("documentExtensions", WebCrawler.documentExtensions);
		
		//Extracting the relevant URLs from the given HTML.
		HtmlParser parser = new HtmlParser(currUrl, currHtml, exts);
		parser.parse();
		
		//Filling the URL lists with downloadable data
		ArrayList<String> imgUrls = parser.getImagesUrls();
		ArrayList<String> videoUrls = parser.getVideosUrls();
		ArrayList<String> docUrls = parser.getDocsUrls();
		ArrayList<String> hrefUrls = parser.getHrefUrls();
		
		Log.d("Sending images to downloads");
		sendToDownload(imgUrls, DownloaderTask.RESOURCE_TYPE_IMG);
		Log.d("Sending videos to downloads");
		sendToDownload(videoUrls, DownloaderTask.RESOURCE_TYPE_VIDEO);
		Log.d("Sending documents to downloads");
		sendToDownload(docUrls, DownloaderTask.RESOURCE_TYPE_DOC);
		Log.d("Sending HREFs to downloads");
		sendToDownload(hrefUrls, DownloaderTask.RESOURCE_TYPE_HREF);
		
		decreaseNumOfAnalyzersAlive();
	}
	
	/**
	 * Send urls to download. Actually starts a DownloaderTask for each url in the list.
	 * @param urls - the urls list
	 * @param resourceType - if it's a HREF or other. Each type needs different handling.
	 */
	private void sendToDownload(ArrayList<String> urls, int resourceType) {
		for (String url : urls) {
			if (!webCrawler.getVisitedUrls().contains(url)) {
				Log.d(String.format("non-visited URL is found! send to downloader : url = %s", url));				
				downloadersPool.submit(new DownloaderTask(url, resourceType, downloadersPool, analyzersPool));
			}
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

	private void increaseNumOfAnalyzersAlive () {
		synchronized (numAnalyzersAliveLock) {
			numAnalyzersAlive++;
		}
	}
	private void decreaseNumOfAnalyzersAlive() {
		synchronized (numAnalyzersAliveLock) {
			numAnalyzersAlive--;
			Log.d("remaining items in Analyzers queue: " + numAnalyzersAlive);
		}
		
		webCrawler.checkIfFinished();
	}
	public static int getNumOfAnalyzersAlive() {
		synchronized (numAnalyzersAliveLock) {
			return numAnalyzersAlive;
		}
	}
}
