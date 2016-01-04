import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * An expansion to BufferedReader that is capable of reading bytes from an InputStream until CRLF is read.
 * Also capable of reading specific amount of bytes.
 */
public class CRLFBufferedReader extends BufferedReader {
	
	public CRLFBufferedReader(Reader in, int sz) {
		super(in, sz);
	}

	public CRLFBufferedReader(Reader in) {
		super(in);
	}

	/**
	 * Read a line until CRLF (\r\n) sequence is met.
	 * 
	 * @return the line without CRLF, <code>null</code> if end of stream.
	 * @throws IOException
	 */
	public String readCRLFLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		
		int i = read();
		while(i != -1) {
			char c = (char) i;
			sb.append(c);
			
			int length = WebServer.CRLF.length();
			if (sb.length() >= length && sb.substring(sb.length() - length).equals(WebServer.CRLF)) {
				
				return sb.toString();
			}
			
			i = read();
		}
		
		//If the end of the stream was reached but there are characters to return, return them
		//And in the next call to read() c wil be '-1' and 'null' will be returned.
		if (sb.length() > 0) {
			return sb.toString();
		}
		
		return null;
	}

	/**
	 * Reads from InputStream <code>expectedContentLength</code> bytes.
	 * No timeout is set here, so make sure that an external timeout is supervising.
	 * 
	 * @param expectedContentLength the amount of bytes to read 
	 * @return The read data as String
	 * @throws IOException
	 */
	public String readUntil(int expectedContentLength) throws IOException {
		
		StringBuilder result = new StringBuilder();
		
		for (int i=0; i < expectedContentLength; i++) {
			char c = (char) read();
			result.append(c);
		}
		
		return result.toString();
	}
}
