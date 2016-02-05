import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

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
		increaseNumOfAnalyzersAlive();
	}

	@Override
	public void run() {
		Log.d(String.format("AnalyzerTask is running on %s", currUrl));
		HashMap<String, ArrayList<String>> exts = new HashMap<>();
		exts.put("imageExtensions", WebCrawler.imageExtensions);
		exts.put("videoExtensions", WebCrawler.videoExtensions);
		exts.put("documentExtensions", WebCrawler.documentExtensions);
		
		HtmlParser parser = new HtmlParser(currUrl, currHtml, exts);
		parser.parse();
		
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
			webCrawler.checkIfFinished();
		}
	}
	public static int getNumOfAnalyzersAlive() {
		synchronized (numAnalyzersAliveLock) {
			return numAnalyzersAlive;
		}
	}
}
