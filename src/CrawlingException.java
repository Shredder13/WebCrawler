public class CrawlingException extends Exception {

	private static final long serialVersionUID = 32485L;

	String error;

	public CrawlingException(String error) {
		this.error = error;
	}

	public String getError() {
		return error;
	}

}
