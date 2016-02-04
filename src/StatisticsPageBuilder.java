import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

public class StatisticsPageBuilder {
	
	CrawlData cd;
	
	public StatisticsPageBuilder(CrawlData crawlData) {
		cd = crawlData;
	}
	
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
			return String.format("%s-%d%02d%02d-%02d%02d%02d.html", hostName, year, month, day, hour, minute, second);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return "domain-name-error";
		}
	}

	private void avgRtt(StringBuilder htmlSb) {
		String avgRtt = cd.get(CrawlData.AVG_RTT).toString();
		htmlSb.append(String.format("<p>Average RTT: %s ms.</p>", avgRtt));
	}
	
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
	
	private void connectedDomains(StringBuilder htmlSb) {
		HashSet<String> connectedDomains = (HashSet<String>) cd.get(CrawlData.CONNECTED_DOMAINS);
		htmlSb.append("<p>Connected domains:<br><ul>");
		for (String domain : connectedDomains) {
			htmlSb.append(String.format("<li>%s</li>", domain));
			//TODO: add links to "connected domains".
		}
		htmlSb.append("</ul></p>");
	}

	private void inernalExternalLinkgs(StringBuilder htmlSb) {
		String numOfInternalLinks = cd.get(CrawlData.NUM_OF_INTERNAL_LINKS).toString();
		String numOfExternalLinks = cd.get(CrawlData.NUM_OF_EXTERNAL_LINKS).toString();
		htmlSb.append(String.format("<p># of internal links %s.<br># of external links %s.</p>", numOfInternalLinks, numOfExternalLinks));
	}

	private void pages(StringBuilder htmlSb) {
		String numOfPages = cd.get(CrawlData.NUM_OF_PAGES).toString();
		String sizeOfPages = cd.get(CrawlData.SIZE_OF_PAGES).toString();
		htmlSb.append(String.format("<p>total %s pages (%s bytes).</p>", numOfPages, sizeOfPages));
	}

	private void docs(StringBuilder htmlSb) {
		String numOfDocs = cd.get(CrawlData.NUM_OF_DOCUMENTS).toString();
		String sizeOfDocs = cd.get(CrawlData.SIZE_OF_DOCUMENTS).toString();
		htmlSb.append(String.format("<p>total %s documents (%s bytes).</p>", numOfDocs, sizeOfDocs));
	}

	private void videos(StringBuilder htmlSb) {
		String numOfVideos = cd.get(CrawlData.NUM_OF_VIDEOS).toString();
		String sizeOfVideos = cd.get(CrawlData.SIZE_OF_VIDEOS).toString();
		htmlSb.append(String.format("<p>total %s videos (%s bytes).</p>", numOfVideos, sizeOfVideos));
	}

	private void images(StringBuilder htmlSb) {
		String numOfImages = cd.get(CrawlData.NUM_OF_IMAGES).toString();
		String sizeOfImages = cd.get(CrawlData.SIZE_OF_IMAGES).toString();
		
		htmlSb.append(String.format("<p>total %s images (%s bytes).</p>", numOfImages, sizeOfImages));
	}

	private void robots(StringBuilder htmlSb) {
		String robots = cd.get(CrawlData.RESPECT_ROBOTS_TXT).toString();
		htmlSb.append(String.format("<p>Respect robots.txt: %s.</p>", robots));
	}
}