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

/**
 * CrawlerHttpConnection is used to perform a request to a given URL, and retrieve the response.
 */
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
	
	/**
	 * Extracts host, port & path from the given url.
	 * @param urlStr
	 * @throws MalformedURLException
	 */
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
	
	/**
	 * Sends the HTTP request, and retrieve the response.
	 * Also updates the average RTT.
	 * @return a CrawlerHttpConnection.Response instance, representing the response. 
	 * @throws UnknownHostException for the socket
	 * @throws IOException if there's a problem with reading or writing to the socket.
	 */
	@Nullable
	public Response getResponse() throws UnknownHostException, IOException {
		
		//extract host, port & path from the URL.
		extractConnectionDetails(urlStr);
		
		Log.d("connecting...");
		socket = new Socket(host, port);
		Log.d("Sending Request...");
		
		long startMillis = System.currentTimeMillis();
		//Send request
		sendRequest();
		long endMillis = System.currentTimeMillis();
		
		//Reading response
		BufferedReader socketInputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		StringBuilder responseSb = new StringBuilder();
		Log.d("Waiting for response...");
		
		int c;
		while((c = socketInputStream.read()) != -1) {
			responseSb.append((char) c);
		}
		Log.d("Done waiting.");
		
		socket.close();
		
		response.rtt = endMillis - startMillis;
		
		//Return the response object only if the response is not broken.
		if (parseHttpResponse(responseSb.toString())) {
			return response;
		}
		
		return null;
	}
	
	/**
	 * Sends the HTTP requset.
	 * @throws IOException
	 */
	private void sendRequest() throws IOException {
		
		StringBuilder reqSb = new StringBuilder();
		reqSb.append(String.format("%s %s %s", method.toString(), path, version.toString()));
		reqSb.append(WebServer.CRLF);
		reqSb.append(String.format("host: %s", host));
		reqSb.append(WebServer.CRLF);
		reqSb.append("referer: 127.0.0.1");
		reqSb.append(WebServer.CRLF);
		
		BufferedWriter socketOutputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		reqSb.append(WebServer.CRLF);
		
		socketOutputStream.write(reqSb.toString());
		socketOutputStream.flush();
	}
	
	/**
	 * Verifying that the response is well formed.
	 * @param responseStr
	 * @return
	 * @throws IOException if there's any error reading the response.
	 */
	private boolean parseHttpResponse(String responseStr) throws IOException {
		CRLFBufferedReader reader = new CRLFBufferedReader(new StringReader(responseStr));
		
		//Read the first line, validate it's correct
		String resLine = reader.readLine();
		boolean resLineOk = validateResponseLine(resLine);
		
		//Read the headers
		String line;
		ArrayList<String> headersList = new ArrayList<>();
		while(!(line = reader.readCRLFLine()).equals(WebServer.CRLF)) {
			headersList.add(line);
		}
		fillResponseHeaders(headersList);
		
		//read body, if has any.
		if (response.headers.containsKey("content-length")) {
			int contentLength = Integer.valueOf(response.headers.get("content-length"));
			response.body = reader.readUntil(contentLength);
		} else {
			StringBuilder sb = new StringBuilder();
			int c;
			while ((c = reader.read()) != -1) {
				sb.append((char)c);
			}
			response.body = sb.toString();
			response.headers.put("content-length", String.valueOf(response.body.length()));
		}
		
		return resLineOk;
	}

	/**
	 * validating that the fifrst line of the response is well formed.
	 * @param resLine
	 * @return
	 */
	private boolean validateResponseLine(String resLine) {
		Pattern p = Pattern.compile("^(HTTP\\/1\\.1|HTTP\\/1\\.0) ([0-9]+ \\w[\\w\\s]*)$");
		Matcher m = p.matcher(resLine);
		if (m.matches()) {
			String httpVersion = m.group(1);
			String httpCode = m.group(2);
			
			boolean httpVerOk = httpVersion != null && (httpVersion.equals(HTTP_VERSION.HTTP_1_0.toString())
														|| httpVersion.equals(HTTP_VERSION.HTTP_1_1.toString()));
			
			//Accespts only HTTP 200 OK & 301 MOVED codes. We ignore others.
			boolean httpCodeOk = httpCode != null && (httpCode.equals(HTTP_CODE.C200_OK.toString())
														|| httpCode.equals(HTTP_CODE.ERR_301_MOVED_PERMANENTLY.toString()));
			
			if (httpVerOk && httpCodeOk) {
				response.version = HTTP_VERSION.fromString(httpVersion);
				response.code = HTTP_CODE.fromString(httpCode);
				return true;
			}
		}
		
		return false;
	}
	
	private void fillResponseHeaders(List<String> headersList) {
		Pattern pattern = Pattern.compile("^([^ ]+): ?(.+)\\r\\n$");
		
		for (String s : headersList) {
			Matcher matcher = pattern.matcher(s); 
			if (matcher.matches()) {
				String key = matcher.group(1).toLowerCase();
				String value = matcher.group(2).toLowerCase();
				
				if (key != null && value != null) {
					response.headers.put(key, value);
				}
			}
		}
	}
	
	/**
	 * Class representing the response to the matching request.
	 */
	public class Response {
		private HTTP_VERSION version;
		private HTTP_CODE code;
		private HashMap<String,String> headers;
		private String body;
		private long rtt; 
		
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
		public long getRTT() {
			return rtt;
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
}