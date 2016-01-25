import java.io.IOException;

public class DownloaderTask extends Task {

	public static final int RESOURCE_TYPE_HREF = 0;
	public static final int RESOURCE_TYPE_IMG = 1;
	public static final int RESOURCE_TYPE_VIDEO = 2;
	public static final int RESOURCE_TYPE_DOC = 3;
	
	ThreadPool downloadersPool;
	ThreadPool analyzersPool;
	
	private int downloaderType;
	private String url;
	
	WebCrawler webCrawler;
	
	public DownloaderTask(String url, int downloaderType, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
		this.downloaderType = downloaderType;
		this.url = url;
		webCrawler = WebCrawler.getInstance();
	}

	@Override
	public void run() {
		switch(downloaderType) {
		case RESOURCE_TYPE_IMG:
		case RESOURCE_TYPE_VIDEO:
		case RESOURCE_TYPE_DOC:
			//TODO: Just send HEAD request
			break;
		
		default:
		case RESOURCE_TYPE_HREF:
			//TODO: Download the HTML (send HTTP request).
			//TODO: start an analyzer
			break;
		}
		
		//TODO: Whatever was downloaded, it should update the CrawlData:
		//webCrawler.getCrawlData().put(key, value);
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
