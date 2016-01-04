import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

/**
 * ListenTask  manages the request-response flow.
 * ListenTask is in charge of keeping the connection alive, if instructed to, by the HTTP/1.x protocol.  
 *
 */
public class ListenTask extends Task {

	private Socket mSocket;
	private HTTPRequest mHttpReq;
	
	//When true, the connection will never be closed because of the server,
	//and after successful request-response cycle we will 'reset' the listen task,
	//so we're able to receive new HTTP request on the same connection (if it's HTTP/1.1 of course)
	private boolean keepAlive = true;

	public ListenTask(Socket socket) {
		mSocket = socket;
		mHttpReq = new HTTPRequest();
	}

	/**
	 * Reads input from the client, parse it, and sends the response according to the results.
	 */
	@Override
	public void run() {
		
		HTTP_CODE httpCode = null;
		
		//If there's a socket error, we won't send response.
		boolean socketError = false;
		
		try {
			mSocket.setSoTimeout(WebServer.SOCKET_TIMEOUT_MILLIS);
		} catch (SocketException e2) {
			Log.d("Socket error while setting it's timeout. Closing connection");
			keepAlive = false;
			try { mSocket.close(); }
			catch (IOException e) { Log.d("Error closing socket"); }
		}

		while (keepAlive) {
			try {
				
				//A reader that reads until CRLF is read.
				CRLFBufferedReader clientInputReader = new CRLFBufferedReader(new InputStreamReader(mSocket.getInputStream()));
				
				//read & print the request headers
				String requestHeadersStr = readRequestHeaders(clientInputReader);
				mHttpReq.setRawRequestData(requestHeadersStr);
				Log.d(requestHeadersStr);
				
				//parse all the request
				parseHTTPRequest(requestHeadersStr, clientInputReader);
				
				//validate the HTTP request
				validateHTTPRequest();
				
				httpCode = HTTP_CODE.C200_OK;
			} catch (HTTPReqErr e) {
				//Something is wrong in the request. We just set the HTTP_CODE 
				//and the response will be built according to the existing info in the HTTPRequest object.
				
				httpCode = e.getErrCode();
				
				if (httpCode == HTTP_CODE.NO_RESPONSE) {
					socketError = true;
				} else {
					Log.d(e.getMessage());
					//e.printStackTrace();
				}
				
			} catch (IOException e) {
				Log.d("ListenTask.run() : " + e.getMessage());
				socketError = true;
				//e.printStackTrace();
			} finally {				
				try {
					
					if (!socketError) {
						//Sending the response back
						HTTPResponse response = new HTTPResponse(httpCode);
						DataOutputStream clientOutputWriter = new DataOutputStream(mSocket.getOutputStream());
						clientOutputWriter.write(response.buildResponse(mHttpReq));
						clientOutputWriter.flush();
					}
					
					// persistent-connection support
					if (checkKeepAlive() && !socketError) {
						mHttpReq = new HTTPRequest();
					} else {
						mSocket.close();
						keepAlive = false;
						Log.d("ListenTask.run() : socket closed.");
					}
				} catch (IOException e1) {
					Log.d("ListenTask.run() : cannot close socket");

					keepAlive = false;
					//e1.printStackTrace();
				}
			}
		}
		
		Log.d("ListenTask.run() : listen thread have finished.");
	}
	
	private void validateHTTPRequest() throws HTTPReqErr {
		HTTPRequestParseUtil.validateHTTPVersion(mHttpReq);
		HTTPRequestParseUtil.validateHTTPMethod(mHttpReq);
		HTTPRequestParseUtil.validateHTTPPath(mHttpReq);
	}

	/**
	 * Reads all the request, until the request body.
	 * @param clientInputReader
	 * @return a String holding the request-header section.
	 * @throws IOException Thrown for any socket error
	 * @throws HTTPReqErr - If the client disconnected, HTTPReqErr is thrown with code NO_RESPONSE. 
	 */
	private String readRequestHeaders(CRLFBufferedReader clientInputReader) throws IOException, HTTPReqErr {
		StringBuilder result = new StringBuilder();
		
		String line = clientInputReader.readCRLFLine();
		
		if (line == null) {
			//Disconnect
			throw new HTTPReqErr(HTTP_CODE.NO_RESPONSE);
		}
		
		while (line != null && !line.equals(WebServer.CRLF)) {
			result.append(line);
			line = clientInputReader.readCRLFLine();
		}
		
		return result.toString();
	}

	/**
	 * @return <code>true</code> if the connection should be kept alive, <code>false</code> otherwise.
	 */
	private boolean checkKeepAlive(){
		
		HTTP_VERSION version = mHttpReq.getHttpVersion();
		HashMap<String, String> headers = mHttpReq.getHeaders();
		
		if (version == null || headers == null) {
			return false;
		}
		
		boolean http11 = version == HTTP_VERSION.HTTP_1_1;
		
		String connectionHeader = headers.get("connection");
		boolean connectionKeepAlive = connectionHeader != null && connectionHeader.toLowerCase().equals("keep-alive");

		return http11 && connectionKeepAlive;
	}

	/**
	 * HTTP request parsing flow.
	 * 
	 * @param requestHeadersStr String including the request content, excluding the body.
	 * @param clientInputReader The socket input reader
	 * @throws HTTPReqErr Thrown if the request is invalid, or an internal error occurs 
	 * @throws IOException Thrown if a socket error occurs
	 */
	private void parseHTTPRequest(String requestHeadersStr, CRLFBufferedReader clientInputReader) throws HTTPReqErr, IOException {

		//We use StringReader here because we already streamed input from the socket.
		CRLFBufferedReader requestHeadersReader = new CRLFBufferedReader(new StringReader(requestHeadersStr));
		
		//Parse the request line
		parseRequestLine(requestHeadersReader);

		//Parse the headers, save the last line because it might be the first lie of request body.
		parseRequestHeaders(requestHeadersReader);
		
		//Parse body - read the rest from the socket
		parseRequestBody(clientInputReader);
	}
	
	/**
	 * Parses the request body.
	 * @param clientInputReader The socket stream reader
	 *  
	 * @throws IOException Thrown for any socket error
	 */
	private void parseRequestBody(CRLFBufferedReader clientInputReader) throws IOException {
		
		//Parse the request-body. Carefully with the content-length as someone may want to screw us :)
		StringBuilder requestBodySB = new StringBuilder();
		String expectedContentLengthStr = mHttpReq.getHeaders().get("content-length");
		
		if (expectedContentLengthStr != null && expectedContentLengthStr.matches("\\d+")) {
			int expectedContentLength = Integer.valueOf(expectedContentLengthStr);
			
			String content = clientInputReader.readUntil(expectedContentLength);
			requestBodySB.append(content);
			
			mHttpReq.setRequestBody(requestBodySB.toString());
			mHttpReq.setRawRequestData(mHttpReq.getRawRequestData() + requestBodySB.toString());
		}
	}

	/**
	 * Parses the request headers. The method returns the last line it read because it might contain request-body data.
	 * 
	 * @param clientInputReader
	 * @return the last line read from the stream, which might be the first request-body line.
	 * @throws IOException
	 * @throws HTTPReqErr
	 */
	private String parseRequestHeaders(CRLFBufferedReader clientInputReader) throws IOException {
		String line = clientInputReader.readCRLFLine();
		while (line != null && !line.equals(WebServer.CRLF)) {
			HTTPRequestParseUtil.parseHeaderLine(mHttpReq, line);
			line = clientInputReader.readCRLFLine();
		}
		
		return line;
	}

	/**
	 * Parses the first request line.
	 * @param clientInputReader Socket input reader
	 * @throws IOException Thrown for any socket error
	 * @throws HTTPReqErr Thrown if the request is invalid, or internal error occurs while parsing the request
	 */
	private void parseRequestLine(CRLFBufferedReader clientInputReader) throws IOException, HTTPReqErr {
		String requestLine = clientInputReader.readCRLFLine();
		HTTPRequestParseUtil.parseRequestLine(mHttpReq, requestLine);
	}

	/**
	 * Close the connection. Any other method in execution that is reading from it will throw IOException. 
	 */
	@Override
	protected void shutdown() throws IOException {
		if (mSocket != null) {
			mSocket.close();
		}
		
		Log.d("ListenTask is shutting down.");
	}

}
