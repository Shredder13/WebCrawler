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
		
		ArrayList<String> objectUrls = new ArrayList<>();
		objectUrls.addAll(parser.getImagesUrls());
		objectUrls.addAll(parser.getVideosUrls());
		objectUrls.addAll(parser.getDocsUrls());
		
		ArrayList<String> hrefUrls = parser.getHrefUrls();

		//Add to URLs queue only if not visited
		for (String url : objectUrls) {
			if (!webCrawler.getVisitedUrls().contains(url)) {
				downloadersPool.submit(new DownloaderTask(url, DownloaderTask.DOWNLOADER_TYPE_HEAD, downloadersPool, analyzersPool));
				//TODO: Someone should listen for callbacks.
			}
		}
		
		for (String url : hrefUrls) {
			if (!webCrawler.getVisitedUrls().contains(url)) {
				downloadersPool.submit(new DownloaderTask(url, DownloaderTask.DOWNLOADER_TYPE_GET, downloadersPool, analyzersPool));
				//TODO: Someone should listen for callbacks.
			}
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
