import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class AnalyzerTask extends Task {
	
	private String currUrl;
	private String currHtml;
	private WebCrawler webCrawler;
	private ThreadPool downloadersPool;
	private ThreadPool analyzersPool;
	
	public AnalyzerTask(String url, String html, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		currUrl = url;
		currHtml = html;
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
		
		webCrawler = WebCrawler.getInstance();
	}

	@Override
	public void run() {
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

		sendToDownload(imgUrls, DownloaderTask.RESOURCE_TYPE_IMG);
		sendToDownload(videoUrls, DownloaderTask.RESOURCE_TYPE_VIDEO);
		sendToDownload(docUrls, DownloaderTask.RESOURCE_TYPE_DOC);
		sendToDownload(hrefUrls, DownloaderTask.RESOURCE_TYPE_HREF);
	}
	
	private void sendToDownload(ArrayList<String> urls, int resourceType) {
		for (String url : urls) {
			if (!webCrawler.getVisitedUrls().contains(url)) {
				downloadersPool.submit(new DownloaderTask(url, resourceType, downloadersPool, analyzersPool));
			}
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
