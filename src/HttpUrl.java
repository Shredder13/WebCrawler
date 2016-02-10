import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instead of using java.net.URL class, we made this class
 * for the same purpose: separating a given URL parts and validating them. 
 */
public class HttpUrl {

	private static final String URL_REGEX = "^(http|https)?:\\/\\/(.+?)(:(\\d{1,5}))?(\\/?|\\/.*)$";
	
	private String url;
	
	private String protocol;
	private String host;
	private int port;
	private String file;
	
	/**
	 * parses the given URL.
	 * @param url
	 * @throws MalformedURLException if any parsing error arises.
	 */
	public HttpUrl(String url) throws MalformedURLException {
		Matcher m = Pattern.compile(URL_REGEX).matcher(url);
		if (m.matches()) {
			protocol = m.group(1);
			
			host = m.group(2);
			
			String portStr = m.group(4);
			if (portStr != null && portStr.length() > 0) {
				port = Integer.valueOf(portStr);
			} else {
				if (protocol.equals("http")) {
					port = 80;
				} else if (protocol.equals("https")) {
					port = 443;
				} else {
					port = -1;
				}
			}
			
			file = m.group(5);
			if (file == null || file != null && file.length() < 2) {
				file = "/";
			}
		}
		
		if (host == null || protocol == null) {
			throw new MalformedURLException();
		}
		
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getFile() {
		return file;
	}
	public void setFile(String newFile) {
		if (!newFile.startsWith("/")) {
			newFile = "/" + newFile;
		}
		
		file = newFile;
	}
	
	/**
	 * Returns a String in the form of "http://www.walla.com:80/folder1/file1.html" 
	 */
	@Override
	public String toString() {
		int p = port;
		if (p == -1) p = 80;
		return String.format("%s://%s:%d%s", protocol, host, port, file);
	}
	
}
