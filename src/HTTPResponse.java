import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;

public class HTTPResponse {

    protected HTTP_CODE code;
    protected HTTP_VERSION httpVersion;
    protected int contentLength;
    protected HashMap<String, String> headers;
    protected String path;
    protected byte[] body;
    protected boolean isChunked;
    private final int CHUNK_SIZE = 1024;

    HTTPRequest request;

    public HTTPResponse(HTTP_CODE code) {
        this.code = code;
        headers = new HashMap<>();
    }

    public void setCode(HTTP_CODE code) {
        this.code = code;
    }
    
    private void validateHTTPCode() {
		if (code == null) {
        	//If somehow the response is built and there's no HTTP code, it's an error.
        	//it can occur if there's a timeout while still before building the response.
        	code = HTTP_CODE.ERR_500_INTERNAL_SERVER_ERROR;
        }
	}

    private void setHttpVersion() {
        if (request.getHttpVersion() != null) {
            httpVersion = request.getHttpVersion();
        } else {
            httpVersion = HTTP_VERSION.HTTP_1_1;
        }
    }

    /**
     * Adds the connection header to the response
     */
    private void addConnectionHedaer() {
        HashMap<String, String>  tHeaders = request.getHeaders();
        if (tHeaders != null) {
            String connection = tHeaders.get("connection");
            if(connection != null && (connection.equals("close") || connection.equals("keep-alive"))) {
                headers.put("connection", connection);
                return;
            }
        }
        headers.put("connection", "close");
    }

    /**
     * checks if the request has "chunked: yes" header
     * if so we add transfer-encoding: chunked and send the body as a chunked message
     */
    private void checkChunked() {
        if (request.getHeaders() != null) {
            String chunked = request.getHeaders().get("chunked");
            if ((chunked != null) && (chunked.equals("yes"))) {
                headers.put("transfer-encoding", "chunked");
                isChunked = true;
            } else {
                isChunked = false;
                if (body != null ) {
                    contentLength = body.length;
                    headers.put("content-length", String.valueOf(contentLength));
                } else {
                    contentLength = 0;
                }
            }
        }
    }

    /**
     * Sets the path of the requested resource
     * and the content type header by the type of it
     */
    private void setPathAndType() {
        if (request.getPath() != null) {
            if (request.getPath().equals("/")) {
                path = WebServer.defaultPage;
            } else {
                path = request.getPath();
            }
            
            if (code != HTTP_CODE.C200_OK) {
            	//There's an error and we would like to return the equivalent HTML page.
            	//Hence the type is always text/html.
            	headers.put("content-type", CONTENT_TYPE.TEXT_HTML.toString());
            	return;
            }

            // determine content-type
            if (path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".bmp") || path.endsWith(".gif")) {
                headers.put("content-type", CONTENT_TYPE.IMAGE.toString());
            } else if (path.endsWith(".html")) {
                headers.put("content-type", CONTENT_TYPE.TEXT_HTML.toString());
            } else if (path.endsWith(".ico")) {
                headers.put("content-type", CONTENT_TYPE.ICON.toString());
            } else {
                headers.put("content-type", CONTENT_TYPE.APP_OCTSTREAM.toString());
            }
        }
    }

    /**
     * Reads from the given file into a byte[]
     * and sets params_info with the sent content from the request
     * when params_info is requested
     * @param file the file to read from
     * @return byte[] with the bytes of the file which was read
     */
    private byte[] readFile(File file) {
        byte[] bFile;
        try {
            FileInputStream fis = new FileInputStream(file);
            bFile = new byte[(int)file.length()];
            // read until the end of the stream.
            while(fis.available() != 0) {
                fis.read(bFile, 0, bFile.length);
            }

            if (file.getAbsolutePath().endsWith("params_info.html")) {
                //If the file is params_info.html we fill the content with the POST data
            	bFile = buildParamsInfo(bFile);
            } else if (file.getAbsolutePath().endsWith("index.html")) {
            	bFile = buildIndex(bFile);
            } else if (file.getAbsolutePath().endsWith("execResult.html")) {
            	bFile = startCrawler(bFile);
            }
            if (fis != null) {
                fis.close();
            }
        }
        catch(IOException e) {
            Log.d("Could not find file in given path");
            bFile = getDefaultResponseContent();
        }

        return bFile;
    }
    
    private byte[] startCrawler(byte[] bFile) {
    	
    	WebCrawler crawler = WebCrawler.getInstance();
    	String html = new String(bFile);
    	String formHolder = "";
    	StringBuilder historyHolderSb = new StringBuilder();
    	
    	switch(crawler.getState()) {
    	case RUNNING:
    		formHolder = "Crawler already running...";
    		break;
    	default:
    	case IDLE:
			try {
				HashMap<String, String> getParams = request.getGetParamsMap();
				String host = getParams.get("txtDomain");
				boolean portScan = getParams.get("cbPortScan") != null && getParams.get("cbPortScan").equals("on");
				boolean disrespectRobotsTxt = getParams.get("cbDisrespectRobots") != null && getParams.get("cbDisrespectRobots").equals("on");
				crawler.start(host, portScan, disrespectRobotsTxt);
    			formHolder = "Started crawler successfully!";
			} catch (CrawlingException e) {
				formHolder = new String(readFile(new File("crawler_form.html")));
				formHolder += String.format("<br>Crawler failed to start because: %s", e.getMessage());
			}
    		break;
    	}
    	
    	for (String link : crawler.getCrawlingHistory()) {
			historyHolderSb.append(String.format("<a href=\"/%s\">%s</a><br>", link, link.replace("_", "-")));
		}
    	
    	html = String.format(html, formHolder, historyHolderSb.toString());
    	
		return html.getBytes();
	}

	private byte[] buildIndex(byte[] bFile) throws IOException {
    	
    	String indexHtml = new String(bFile);
    	StringBuilder historyHolderSb = new StringBuilder();    	
		String formHolder = new String(readFile(new File("crawler_form.html")));
		
    	WebCrawler crawler = WebCrawler.getInstance();
		for (String link : crawler.getCrawlingHistory()) {
			historyHolderSb.append(String.format("<a href=\"/%s\">%s</a><br>", link, link.replace("_", "-")));
		}
    	
    	indexHtml = String.format(indexHtml, formHolder, historyHolderSb.toString());
    	
    	return indexHtml.getBytes();
    }
    
    private byte[] buildParamsInfo(byte[] bFile) throws IOException {
    	if (request.getPostParamsMap() != null) {
            String cbox1Value = request.getPostParamsMap().get("cbox1");
            String textarea1Value = request.getPostParamsMap().get("textarea1");
            
            if (textarea1Value != null) {
                textarea1Value = URLDecoder.decode(textarea1Value, "UTF-8");
            } else {
            	textarea1Value = "";
            }
            
            if (cbox1Value != null) {
                cbox1Value = URLDecoder.decode(cbox1Value, "UTF-8");
            } else {
            	cbox1Value = "";
            }
            
            String html = new String(bFile);
            html = String.format(html, textarea1Value, cbox1Value);
            bFile = html.getBytes();
        }
    	
    	return bFile;
    }

    /**
     * In case we couldn't read from the files- return a hard coded html
     * @return bytes of the html according to the code
     */
    private byte[] getDefaultResponseContent() {
    	switch(code) {
    	case ERR_400_BAD_REQUEST:
    		return ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>400 Bad Request" +
                    "</title></head><body>400 Bad Request.</body></html>").getBytes();
    	case ERR_404_NOT_FOUND:
    		return ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>404 File Not Found" +
                    "</title></head><body>404 File Not Found.</body></html>").getBytes();
    	case ERR_501_NOT_IMPLEMENTED:
    		return ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>501 Not Implemented" +
                    "</title></head><body>501 Not Implemented.</body></html>").getBytes();
		default:
    	case ERR_500_INTERNAL_SERVER_ERROR:
    		return ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>500 Internal Server Error" +
                    "</title></head><body>500 Internal Server Error.</body></html>").getBytes();
    	}
    }

    /**
     * In case the code was not 200 return error html accordingly
     * @return file to read from
     */
    private File getFileByCode() {
        File file = null;
        switch(code) {
            case ERR_501_NOT_IMPLEMENTED:
                file = new File(WebServer.root + "501.html");
                break;
            case ERR_400_BAD_REQUEST:
                file = new File(WebServer.root + "400.html");
                break;
            case ERR_404_NOT_FOUND:
                file = new File(WebServer.root + "404.html");
                break;
            case ERR_500_INTERNAL_SERVER_ERROR:
                file = new File(WebServer.root + "500.html");
                break;
            default:
                file = new File(WebServer.root + path);
                break;
        }

        return file;
    }

    /**
     * In case the request asked the response as chunked
     * send the body part of the response by chunks and Hex stating how
     * many bytes were send (before the chunk)
     * @param body the regular body of the response
     * @return the chunked body of the response
     */
    private byte[] sendChunked(byte[] body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int length = body.length;
        int copiedBytes = 0;
        try {
            while((copiedBytes + CHUNK_SIZE) < length) {

                // Number of bytes in chunk
                bytes.write(Integer.toHexString(CHUNK_SIZE).getBytes());
                // CRLF
                bytes.write(WebServer.CRLF.getBytes());
                // get CHUNK_SIZE bytes from last place we wrote
                bytes.write(body, copiedBytes, CHUNK_SIZE);
                // CRLF
                bytes.write(WebServer.CRLF.getBytes());
                // advance by CHUNK_SIZE
                copiedBytes += CHUNK_SIZE;
            }

            // calculate how much bytes are left to send
            int bytesLeft = length - copiedBytes;
            // write number of chunk in hex
            bytes.write(Integer.toHexString(bytesLeft).getBytes());
            bytes.write(WebServer.CRLF.getBytes());
            bytes.write(body, copiedBytes, bytesLeft);
            bytes.write(WebServer.CRLF.getBytes());
            bytes.write("0".getBytes());
            bytes.write(WebServer.CRLF.getBytes());
            bytes.write(WebServer.CRLF.getBytes());
        } catch (IOException e) {
            Log.d("Could not write as chunks");
            return body;
        }

        return bytes.toByteArray();
    }

    /**
     * builds a response according to the request and its validity
     * @param request The user's request
     * @return HTTPResponse according to the request given
     */
    public byte[] buildResponse(HTTPRequest request) {
        this.request = request;
        
        validateHTTPCode();
        setHttpVersion();
        setPathAndType();
        addConnectionHedaer();

        HTTP_METHOD reqMethod = request.getHttpMethod();
	    if (reqMethod != null && reqMethod.equals(HTTP_METHOD.TRACE)) {
	        // creates a response that is exactly the request
	        body = request.getRawRequestData().getBytes();
	        headers.put("content-type", CONTENT_TYPE.MSG_HTTP.toString());
	        //headers.put("connection", "close");
	        code = HTTP_CODE.C200_OK;
	    } else if (reqMethod != null && reqMethod.equals(HTTP_METHOD.OPTIONS)) {
	        // creates a response with all methods available and content-length 0
	        headers.put("Allow", reqMethod.methodsList());
	    } else {
	        // get file name by the HTTP_CODE and create a regular file response
	        File file = getFileByCode();
	        body = readFile(file);
	    }
        
        // check and add transfer-encoding header if necessary
        checkChunked();

        // START BUILDING THE RESPONSE :)
        StringBuilder builder = new StringBuilder();
        // HTTP_VERSION + HTTP_CODE + CRLF (first row of response)
        builder.append(httpVersion);
        builder.append(" ");
        builder.append(code);
        builder.append(WebServer.CRLF);

        // add all response headers
        for (String s: this.headers.keySet()) {
            builder.append(s);
            builder.append(": ");
            builder.append(this.headers.get(s));
            builder.append(WebServer.CRLF);
        }

        builder.append(WebServer.CRLF);
        
        // print response head as requested
        System.out.println(builder.toString());

        // if we only want the head
        if ((request.getHttpMethod() != null) && (request.getHttpMethod().equals(HTTP_METHOD.HEAD))) {        	
            return builder.toString().getBytes();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            baos.write(builder.toString().getBytes());
            if (body != null) {
                if (isChunked) {
                    body = sendChunked(body);
                }
                baos.write(body, 0, body.length);
            }
        } catch (IOException e) {
            Log.d("Could not write to ByteArrayOutputStream");
        }
        return baos.toByteArray();
    }
}
