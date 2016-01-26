import java.io.IOException;
import java.net.UnknownHostException;

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
		
		//TODO: if visited, do not download
		
		//TODO: check if external or internal link
		
		try {
			
			CrawlerHttpConnection conn;
			CrawlerHttpConnection.Response response;
			
			switch(downloaderType) {
			case RESOURCE_TYPE_IMG:
			case RESOURCE_TYPE_VIDEO:
			case RESOURCE_TYPE_DOC:
				//TODO: Just send HEAD request
				conn = new CrawlerHttpConnection(HTTP_METHOD.HEAD, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();
				
				//TODO: update BLOB craw data.
				//webCrawler.getCrawlData().put(key, value);
				break;
			default:
			case RESOURCE_TYPE_HREF:
				conn = new CrawlerHttpConnection(HTTP_METHOD.GET, url, HTTP_VERSION.HTTP_1_0);
				response = conn.getResponse();
				conn.close();
				
				analyzersPool.submit(new AnalyzerTask(url, response.getBody(), downloadersPool, analyzersPool));
				
				//TODO: update HTML crawl data.
				//webCrawler.getCrawlData().put(key, value);
				break;
			}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
