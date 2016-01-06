import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class WebCrawler {
	
	enum State {
		IDLE, RUNNING
	}
	private static WebCrawler sInstance;
	
	private State state = State.IDLE;
	private final Object stateLock = new Object();
	
	public static WebCrawler getInstance() {
		if (sInstance == null) {
			sInstance = new WebCrawler();
		}
		
		return sInstance;
	}

	public State getState() {
		synchronized(stateLock) {
			return state;
		}
	}
	private void setState(State s) {
		synchronized(stateLock) {
			state = s;
		}
	}
	
	public ArrayList<String> getCrawlingHistory() {
		ArrayList<String> result = new ArrayList<>();
		
		try{
			String root = WebServer.root;
			File rootFolder = new File(root);
			File[] files = rootFolder.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return name.matches("^\\w+_\\d+_\\d+\\.html");
				}
			});	
			
			if (files != null) {
				for (File f : files) {
					result.add(f.getName());
				}
			}
			
			Collections.sort(result, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					return o1.compareTo(o2);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public boolean startCrawling(String host) throws CrawlingException {
		
		//TODO:test the host string
		
		setState(State.RUNNING);
		return true;
	}
}
