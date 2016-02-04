import java.util.HashMap;
import java.util.HashSet;

public class CrawlData {

	public static final String OPENNED_PORTS = "OPENNED_PORTS";
	public static final String RESPECT_ROBOTS_TXT = "RESPECT_ROBOTS_TXT";
	public static final String NUM_OF_IMAGES = "NUM_OF_IMAGES";
	public static final String SIZE_OF_IMAGES = "SIZE_OF_IMAGES";
	public static final String NUM_OF_VIDEOS = "NUM_OF_VIDEOS";
	public static final String SIZE_OF_VIDEOS = "SIZE_OF_VIDEOS";
	public static final String NUM_OF_DOCUMENTS = "NUM_OF_DOCUMENTS";
	public static final String SIZE_OF_DOCUMENTS = "SIZE_OF_DOCUMENTS";
	public static final String NUM_OF_PAGES = "NUM_OF_PAGES";
	public static final String SIZE_OF_PAGES = "SIZE_OF_PAGES";
	public static final String NUM_OF_INTERNAL_LINKS = "NUM_OF_INTERNAL_LINKS";
	public static final String NUM_OF_EXTERNAL_LINKS = "NUM_OF_EXTERNAL_LINKS";
	public static final String CONNECTED_DOMAINS = "CONNECTED_DOMAINS";
	public static final String AVG_RTT = "AVG_RTT";
	
	private final Object lock = new Object();
	
	private HashMap<String, Object> data;
	
	private double sumRTT;
	private double numRTT;
	

	public CrawlData() {
		data = new HashMap<>();
		init();
	}
	
	private void init() {
		data.put(RESPECT_ROBOTS_TXT, false);
		data.put(NUM_OF_IMAGES, 0L);
		data.put(SIZE_OF_IMAGES, 0L);
		data.put(NUM_OF_VIDEOS, 0L);
		data.put(SIZE_OF_VIDEOS, 0L);
		data.put(NUM_OF_DOCUMENTS, 0L);
		data.put(SIZE_OF_DOCUMENTS, 0L);
		data.put(NUM_OF_PAGES, 0L);
		data.put(SIZE_OF_PAGES, 0L);
		data.put(NUM_OF_INTERNAL_LINKS, 0L);
		data.put(NUM_OF_EXTERNAL_LINKS, 0L);
		data.put(CONNECTED_DOMAINS, new HashSet<String>());
		data.put(AVG_RTT, 0L);
		
		sumRTT = 0;
		numRTT = 0;
		
		//NOTE: the ports are added dynamically, if requested.
		//If data.get(OPENNED_PORTS) == null then no port scan asked.
	}
	
	public Object get(String key) {
		synchronized (lock) {
			return data.get(key);
		}
	}
	
	public void put(String key, Object value) {
		synchronized (lock) {
			data.put(key, value);
		}
	}
	
	public void clear() {
		synchronized (lock) {
			init();
		}
	}
	
	public void updateAvgRTT(long rtt) {
		synchronized (lock) {
			sumRTT += rtt;
			numRTT++;
			data.put(AVG_RTT, sumRTT/numRTT);
		}
	}
}
