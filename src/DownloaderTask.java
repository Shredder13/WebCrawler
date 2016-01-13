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
		try {
			switch(downloaderType) {
			case RESOURCE_TYPE_IMG:
			case RESOURCE_TYPE_VIDEO:
			case RESOURCE_TYPE_DOC:
				WebServerHttpRequest req = new WebServerHttpRequest();
				req.setHttpMethod(HTTP_METHOD.HEAD.toString())
					.setHttpVersion(HTTP_VERSION.HTTP_1_0.toString())
					.setPath(url);
				break;
			
			default:
			case RESOURCE_TYPE_HREF:
				break;
			}
		} catch (HTTPReqErr e) {
			
		}
	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
