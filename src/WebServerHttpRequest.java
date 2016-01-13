import java.util.HashMap;

public class WebServerHttpRequest {

	private String root;
	private String defaultPage;
	private HTTP_VERSION httpVer;
	private String path;
	private HTTP_METHOD httpMethod;
	private HashMap<String, String> headers;
	private String requestBody;
	private HashMap<String, String> getParams;
	private HashMap<String, String> postParams;

	private String rawRequestData;

	public WebServerHttpRequest() {
		headers = new HashMap<>();
		getParams = new HashMap<>();
		postParams = new HashMap<>();
		this.root = WebServer.root;
		this.defaultPage = WebServer.defaultPage;
	}

	public WebServerHttpRequest setHttpVersion(String version) throws HTTPReqErr {

		if (version == null) {
			return this;
		}
		
		if (version.equals(HTTP_VERSION.HTTP_1_0.toString())) {
			httpVer = HTTP_VERSION.HTTP_1_0;
		} else if (version.equals(HTTP_VERSION.HTTP_1_1.toString())) {
			httpVer = HTTP_VERSION.HTTP_1_1;
		}
		
		return this;
	}
	
	public WebServerHttpRequest setPath(String path) throws HTTPReqErr {
		
		if (path == null || path != null && path.length() == 0) {
			//If somehow the path is null or empty, it's a bad request
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
		
		//Flip slashes
		path = path.replace('\\', '/');
		
		if (!path.startsWith("/")) {
			//Extra kindness to the client
			path = "/" + path;
		}
		
		//Replace all /../ with /
		path.replaceAll("/\\.\\./", "/");
		
		this.path = path;
		return this;
	}

	public WebServerHttpRequest setHttpMethod(String method) throws HTTPReqErr {

		if (method.length() == 0) {
			throw new HTTPReqErr(HTTP_CODE.ERR_400_BAD_REQUEST);
		}
		if (method.equals(HTTP_METHOD.GET.toString())) {
			httpMethod = HTTP_METHOD.GET;
		} else if (method.equals(HTTP_METHOD.POST.toString())) {
			httpMethod = HTTP_METHOD.POST;
		} else if (method.equals(HTTP_METHOD.TRACE.toString())) {
			httpMethod = HTTP_METHOD.TRACE;
		} else if (method.equals(HTTP_METHOD.HEAD.toString())) {
			httpMethod = HTTP_METHOD.HEAD;
		} else if (method.equals(HTTP_METHOD.OPTIONS.toString())){
			httpMethod = HTTP_METHOD.OPTIONS;
		}
		
		return this;
	}

	public WebServerHttpRequest addHeader(String headerKey, String headerValue) {

		if (headerKey.toLowerCase().equals("content-length")) {
			try {
				int length = Integer.valueOf(headerValue);
				if (length < 0) {
					return this;
				}
			} catch (NumberFormatException e) {
				//Ignore wrong values, don't put that header in.
				return this;
			}
		}

		headers.put(headerKey, headerValue);
		return this;
	}

	public WebServerHttpRequest setRequestBody(String requestBody) {
		
		this.requestBody = requestBody;
		
		if (httpMethod == HTTP_METHOD.POST) {
			setParams(postParams, this.requestBody);
		}
		
		return this;
	}
	
	public WebServerHttpRequest setParams(HashMap<String, String> paramsMap, String params) {
		String[] pairs = params.split("&");
		for (String pair : pairs) {
			int endOfKey = pair.indexOf('=');
			String key = "";
			String val = "";
			if (endOfKey != -1) {
				key = pair.substring(0, endOfKey);
				
				int startOfVal = endOfKey + 1;
				val = pair.substring(startOfVal, pair.length());
				
				if (key.matches("^[a-zA-Z]\\w*") && val.length() > 0) {
					paramsMap.put(key, val);
				}
			}
		}
		
		return this;
	}
	
	public HTTP_VERSION getHttpVersion() {
		return httpVer;
	}

	public String getPath() {
		return path;
	}

	public HTTP_METHOD getHttpMethod() {
		return httpMethod;
	}

	public HashMap<String, String> getHeaders() {
		return headers;
	}
	
	public String getRequestBody() {
		return requestBody;
	}

	public HashMap<String, String> getGetParamsMap() {
		return getParams;
	}

	public HashMap<String, String> getPostParamsMap() {
		return postParams;
	}

	public void setRawRequestData(String requestHeadersStr) {
		this.rawRequestData = requestHeadersStr;		
	}
	
	public String getRawRequestData() {
		return this.rawRequestData;
	}
}
