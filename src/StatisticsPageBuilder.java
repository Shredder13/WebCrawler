import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

/**
 * Class for building the statistics HTML page, from the WebCrawler's CrawlData.
 *
 */
public class StatisticsPageBuilder {
	
	CrawlData cd;
	
	public StatisticsPageBuilder(CrawlData crawlData) {
		cd = crawlData;
	}
	
	/**
	 * Builds the HTML page according to the CrawlData given in the constructor.
	 * @return
	 */
	public boolean build() {
		try {
			StringBuilder htmlSb = new StringBuilder("<html><head></head><body>");
			
			robots(htmlSb);
			images(htmlSb);
			videos(htmlSb);
			docs(htmlSb);
			pages(htmlSb);
			inernalExternalLinkgs(htmlSb);
			connectedDomains(htmlSb);
			openedPorts(htmlSb);
			avgRtt(htmlSb);
			linkToMainPage(htmlSb);
			
			htmlSb.append("</body></html>");
			
			FileWriter writer = new FileWriter(getFileName());
			writer.write(htmlSb.toString());
			writer.flush();
			writer.close();
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Insert the main-page link part to the HTML builder
	 * @param htmlSb
	 */
	private void linkToMainPage(StringBuilder htmlSb) {
		htmlSb.append(String.format("<p>Link to the main page: <a href=\"http://localhost:%d/\">here</a></p>", WebServer.port));
	}

	/**
	 * Create the statistics file name in the form of "domain_yyyymmdd_hhmmss.html".
	 * @return
	 */
	private String getFileName() {
		Calendar c = Calendar.getInstance();
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH);
		int day = c.get(Calendar.DAY_OF_MONTH);
		int hour = c.get(Calendar.HOUR_OF_DAY);
		int minute = c.get(Calendar.MINUTE);
		int second = c.get(Calendar.SECOND);
		
		try {
			String hostName = new HttpUrl(WebCrawler.getInstance().getHostUrl()).getHost();
			return String.format("%s_%04d%02d%02d_%02d%02d%02d.html", hostName, year, month, day, hour, minute, second);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return "domain-name-error";
		}
	}

	/**
	 * Insert the average RTT statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void avgRtt(StringBuilder htmlSb) {
		String avgRtt = cd.get(CrawlData.AVG_RTT).toString();
		htmlSb.append(String.format("<p>Average RTT: %s ms.</p>", avgRtt));
	}
	
	/**
	 * Insert the opened ports statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void openedPorts(StringBuilder htmlSb) {
		if (cd.get(CrawlData.OPENNED_PORTS) != null) {
			List<Integer> ports = (List<Integer>) cd.get(CrawlData.OPENNED_PORTS);
			htmlSb.append("<p>Opened ports: ");
			for (int i=0; i<ports.size(); i++) {
				htmlSb.append(ports.get(i));
				
				if (i != ports.size() - 1) {
					htmlSb.append(", ");
				}
			}
			
			htmlSb.append("</p>");
		}
	}
	
	/**
	 * Insert the connected-domains statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void connectedDomains(StringBuilder htmlSb) {
		String root = WebServer.root;
		File rootFolder = new File(root);
		File[] files = rootFolder.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name.matches("^.+_\\d+_\\d+\\.html");
			}
		});	
		
		HashSet<String> historyDomains = new HashSet<>();
		if (files != null) {
			for (File f : files) {
				String fname = f.getName();
				historyDomains.add(fname.substring(0, fname.indexOf('_')));
			}
		}
		
		HashSet<String> connectedDomains = (HashSet<String>) cd.get(CrawlData.CONNECTED_DOMAINS);
		htmlSb.append("<p>Connected domains:<br><ul>");
		
		for (String domain : connectedDomains) {
			htmlSb.append("<li>");
			if (historyDomains.contains(domain)) {
				htmlSb.append(String.format("<a href=\"http://%s/\">%s</a>", domain, domain));
			} else {
				htmlSb.append(domain);
			}
			htmlSb.append("</li>");
		}
		htmlSb.append("</ul></p>");
	}
	
	/**
	 * Insert the internal & external links statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void inernalExternalLinkgs(StringBuilder htmlSb) {
		String numOfInternalLinks = cd.get(CrawlData.NUM_OF_INTERNAL_LINKS).toString();
		String numOfExternalLinks = cd.get(CrawlData.NUM_OF_EXTERNAL_LINKS).toString();
		htmlSb.append(String.format("<p># of internal links %s.<br># of external links %s.</p>", numOfInternalLinks, numOfExternalLinks));
	}

	/**
	 * Insert the pages statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void pages(StringBuilder htmlSb) {
		String numOfPages = cd.get(CrawlData.NUM_OF_PAGES).toString();
		String sizeOfPages = cd.get(CrawlData.SIZE_OF_PAGES).toString();
		htmlSb.append(String.format("<p>total %s pages (%s bytes).</p>", numOfPages, sizeOfPages));
	}

	/**
	 * Insert the documents statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void docs(StringBuilder htmlSb) {
		String numOfDocs = cd.get(CrawlData.NUM_OF_DOCUMENTS).toString();
		String sizeOfDocs = cd.get(CrawlData.SIZE_OF_DOCUMENTS).toString();
		htmlSb.append(String.format("<p>total %s documents (%s bytes).</p>", numOfDocs, sizeOfDocs));
	}

	/**
	 * Insert the videos statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void videos(StringBuilder htmlSb) {
		String numOfVideos = cd.get(CrawlData.NUM_OF_VIDEOS).toString();
		String sizeOfVideos = cd.get(CrawlData.SIZE_OF_VIDEOS).toString();
		htmlSb.append(String.format("<p>total %s videos (%s bytes).</p>", numOfVideos, sizeOfVideos));
	}

	/**
	 * Insert the images statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void images(StringBuilder htmlSb) {
		String numOfImages = cd.get(CrawlData.NUM_OF_IMAGES).toString();
		String sizeOfImages = cd.get(CrawlData.SIZE_OF_IMAGES).toString();
		
		htmlSb.append(String.format("<p>total %s images (%s bytes).</p>", numOfImages, sizeOfImages));
	}
	
	/**
	 * Insert the robots statistics part to the HTML builder
	 * @param htmlSb
	 */
	private void robots(StringBuilder htmlSb) {
		String robots = cd.get(CrawlData.DISRESPECT_ROBOTS_TXT).toString();
		htmlSb.append(String.format("<p>Disrespect robots.txt: %s.</p>", robots));
	}
}