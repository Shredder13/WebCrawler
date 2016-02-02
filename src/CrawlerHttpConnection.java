import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.istack.internal.Nullable;

public class CrawlerHttpConnection {
	Socket socket;
	
	HTTP_METHOD method;
	HTTP_VERSION version;
	String urlStr;
	String host;
	String path;
	int port;
	
	Response response;
	
	public CrawlerHttpConnection(HTTP_METHOD method, String urlStr, HTTP_VERSION version) {
		this.urlStr = urlStr;
		this.method = method;
		this.version = version;
		
		response = new Response();
	}
	
	private void extractConnectionDetails(String urlStr) throws MalformedURLException {
		HttpUrl url = new HttpUrl(urlStr);
		
		host = url.getHost();
		
		port = url.getPort();
		if (port == -1) {
			port = 80;
		}
		
		path = url.getFile();
		if (path.equals("")) {
			path = "/";
		}
	}
	
	@Nullable
	public Response getResponse() throws UnknownHostException, IOException {
		
		extractConnectionDetails(urlStr);
		Log.d("connecting...");
		socket = new Socket(host, port);
		Log.d("Sending Request...");
		sendRequest();
		
		BufferedReader socketInputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		char[] cbuf = new char[4096];
		StringBuilder responseSb = new StringBuilder();
		Log.d("Waiting for response...");
		while(socketInputStream.read(cbuf) != -1) {
			responseSb.append(cbuf);
		}
		Log.d("Done waiting.");
		System.out.println(responseSb);
		
		if (parseHttpResponse(responseSb.toString())) {
			return response;
		}
		
		return null;
	}
	
	private void sendRequest() throws IOException {
		BufferedWriter socketOutputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		socketOutputStream.write(String.format("%s %s %s", method.toString(), path, version.toString()));
		socketOutputStream.write(WebServer.CRLF);
		socketOutputStream.write("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2486.0 Safari/537.36 Edge/13.10586");
		socketOutputStream.write(WebServer.CRLF);
		socketOutputStream.write(WebServer.CRLF);
		socketOutputStream.flush();
	}
	
	private boolean parseHttpResponse(String responseStr) throws IOException {
		CRLFBufferedReader reader = new CRLFBufferedReader(new StringReader(responseStr));
		
		//Read the first line, validate it's correct
		String resLine = reader.readLine();
		boolean resLineOk = validateResponseLine(resLine);
		
		//Read the headers, validate that content-length && content-type exists.
		String line;
		ArrayList<String> headersList = new ArrayList<>();
		while(!(line = reader.readCRLFLine()).equals(WebServer.CRLF)) {
			headersList.add(line);
		}
		boolean resHeadersOk = validateResponseHeaders(headersList);
		
		//read body, if has any.
		if (response.headers.containsKey("content-length")) {
			int contentLength = Integer.valueOf(response.headers.get("content-length"));
			response.body = reader.readUntil(contentLength);
		}
		
		return resLineOk && resHeadersOk;
	}

	private boolean validateResponseLine(String resLine) {
		Pattern p = Pattern.compile("^(HTTP\\/1\\.1|HTTP\\/1\\.0) ([0-9]+ \\w+)$");
		Matcher m = p.matcher(resLine);
		if (m.matches()) {
			String httpVersion = m.group(1);
			String httpCode = m.group(2);
			
			boolean httpVerOk = httpVersion != null && (httpVersion.equals(HTTP_VERSION.HTTP_1_0.toString())
														|| httpVersion.equals(HTTP_VERSION.HTTP_1_1.toString()));
			boolean httpCodeOk = httpCode != null && httpCode.equals(HTTP_CODE.C200_OK.toString());
			
			if (httpVerOk && httpCodeOk) {
				response.version = HTTP_VERSION.fromString(httpVersion);
				response.code = HTTP_CODE.C200_OK;
				return true;
			}
		}
		
		return false;
	}
	
	private boolean validateResponseHeaders(List<String> headersList) {
		Pattern pattern = Pattern.compile("^([^ ]+): ?(.+)\\r\\n$");
		
		boolean hasContentLength = false;
		boolean hasContentType = false;
		
		for (String s : headersList) {
			Matcher matcher = pattern.matcher(s); 
			if (matcher.matches()) {
				String key = matcher.group(1);
				String value = matcher.group(2);
				
				if (key != null && value != null) {
					if (!hasContentLength) hasContentLength = key.toLowerCase().equals("content-length");
					if (!hasContentType) hasContentType = key.toLowerCase().equals("content-type");
					response.headers.put(matcher.group(1), matcher.group(2));
				}
			}
		}
		
		return hasContentLength && hasContentType;
	}
	
	public class Response {
		private HTTP_VERSION version;
		private HTTP_CODE code;
		private HashMap<String,String> headers;
		private String body;
		
		public Response() {
			headers = new HashMap<>();
		}
		public HTTP_VERSION getVersion() {
			return version;
		}
		public HTTP_CODE getCode() {
			return code;
		}
		public HashMap<String, String> getHeaders() {
			return headers;
		}
		public String getBody() {
			return body;
		}
	}

	public void close() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				Log.d("CrawlerHttpConnection: error closing socket");
			}
		}
	}
	
	/*public static void main(String[] args) {
		CrawlerHttpConnection req = new CrawlerHttpConnection(HTTP_METHOD.HEAD, "http://localhost:8080/", HTTP_VERSION.HTTP_1_0);
		try {
			req.getResponse();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}*/
}