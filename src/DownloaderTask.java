import java.io.IOException;

public class DownloaderTask extends Task {

	public static int DOWNLOADER_TYPE_GET = 0;
	public static int DOWNLOADER_TYPE_HEAD = 1;
	
	ThreadPool downloadersPool;
	ThreadPool analyzersPool;
	
	public DownloaderTask(String url, int downladerType, ThreadPool downloadersPool, ThreadPool analyzersPool) {
		this.downloadersPool = downloadersPool;
		this.analyzersPool = analyzersPool;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void shutdown() throws IOException {
		// TODO Auto-generated method stub

	}

}
