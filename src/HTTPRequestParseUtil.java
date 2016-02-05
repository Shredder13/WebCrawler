import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A utility for parsing HTTP requests.
 *
 */
public class HTTPRequestParseUtil {

	/**
	 * Parses the first request line. Extracts the method, path and HTTP version.
	 * An HTTPReqErr exception is thrown if anything goes wrong with the request itself. 
	 * @param request The request which stores the data
	 * @param line The request line as read from the client
	 * @throws HTTPReqErr Raised when the method or HTTP version are not supported, or the path is not found,
	 * or it's just a bad request.
	 */
	public static void parseRequestLine(WebServerHttpRequest request, String line) throws HTTPReqErr {
		
		if (line == null) {
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
		
		String regex = "^([a-zA-Z]+) ([^ ]+) (HTTP\\/1\\.1|HTTP\\/1\\.0)\\r\\n$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(line); 
		if (matcher.matches()) {
			
			String method = matcher.group(1);
			String[] pathAndGetParams = matcher.group(2).split("\\?");
			String httpVersion = matcher.group(3);
			
			String path = pathAndGetParams[0];
			if (pathAndGetParams.length == 2) {
				String params = pathAndGetParams[1];
				request.setParams(request.getGetParamsMap(), params);
			}
			
			request.setHttpVersion(httpVersion)
			.setPath(path)
			.setHttpMethod(method);
		} else {
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
	}

	/**
	 * Parses headers in the request. The headers are stored in a hashmap.
	 * 
	 * @param request The request holding the headers map
	 * @param line The header as read from the stream
	 */
	public static void parseHeaderLine(WebServerHttpRequest request, String line) {
		
		//group0 is the key, group1 is the value
		String regex = "^([^ ]+): ?(.+)\\r\\n$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(line); 
		if (matcher.matches()) {
			request.addHeader(matcher.group(1).toLowerCase(), matcher.group(2));
		}
	}
	
	/**
	 * 
	 * @param request
	 * @throws HTTPReqErr With code 400_BAD_ERROR if the httpversion is not 1.0 or 1.1
	 */
	public static void validateHTTPVersion(WebServerHttpRequest request) throws HTTPReqErr {
		HTTP_VERSION version = request.getHttpVersion();
		if (version == null) {
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
		
		if (!version.equals(HTTP_VERSION.HTTP_1_0) && !version.equals(HTTP_VERSION.HTTP_1_1)) {
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
	}
	
	/**
	 * 
	 * @param request
	 * @throws HTTPReqErr With code 404_NOT_FOUND if the resource requested is not found
	 */
	public static void validateHTTPPath(WebServerHttpRequest request) throws HTTPReqErr {
		
		String path = request.getPath();
		String root = WebServer.root;
		
		if (path == null || path != null && path.length() == 0) {
			//If somehow the path is null or empty, it's a bad request
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
		
		if (!path.equals("/")) {
			String cleanRoot = root;
			if (root.charAt(root.length()-1) == '/') {
				//If the last char of 'root' is '/', we remove it for consistency.
				cleanRoot = root.substring(0, root.length()-1);
			}
			
			File file = new File(cleanRoot + path);
			if (!file.isFile() && !file.exists()) {
				throw new HTTPReqErr(HTTP_CODE.ERR_404_NOT_FOUND);
			}
			
			if (path.matches("\\/(.+?_\\d{8}_\\d{6}\\.html)") || path.endsWith("execResult.html")) {
				String referer = request.getHeaders().get("referer");
				if (!(referer != null && (referer.contains("127.0.0.1") || referer.contains("localhost")))) {
					throw new HTTPReqErr(HTTP_CODE.ERR_403_FORBIDDEN);
				}
			}
		}		
	}
	
	/**
	 * 
	 * @param request
	 * @throws HTTPReqErr With code 501_NOT_IMPLEMENTED if the method is not implemented
	 */
	public static void validateHTTPMethod(WebServerHttpRequest request) throws HTTPReqErr {
		HTTP_METHOD method = request.getHttpMethod();
		if (method == null) {
			throw new HTTPReqErr(HTTP_CODE.ERR_501_NOT_IMPLEMENTED);
		}
	}
}
