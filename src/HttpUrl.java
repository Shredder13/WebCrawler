import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUrl {

	private static final String URL_REGEX = "^(http|https)?:\\/\\/(.+?)(:(\\d{1,5}))?(\\/?|\\/.*)$";
	
	private String url;
	
	private String protocol;
	private String host;
	private int port;
	private String file;
	
	public HttpUrl(String url) throws MalformedURLException {
		Matcher m = Pattern.compile(URL_REGEX).matcher(url);
		if (m.matches()) {
			protocol = m.group(1);
			
			host = m.group(2);
			
			String portStr = m.group(4);
			if (portStr != null && portStr.length() > 0) {
				port = Integer.valueOf(portStr);
			} else {
				port = -1;
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
	
	public static void main(String[] args) {
		try {
			HttpUrl url = new HttpUrl("http://www.walla.com:80/dude/index.html");
			System.out.println(url.getUrl());
			System.out.println(url.getProtocol());
			System.out.println(url.getHost());
			System.out.println(url.getPort());
			System.out.println(url.getFile());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
