public class HTTPReqErr extends Exception {
	
	private HTTP_CODE errCode;
	
	public HTTPReqErr(HTTP_CODE errCode) {
		super(errCode.toString());
		this.errCode = errCode;
	}
	public HTTP_CODE getErrCode() {
		return errCode;
	}
}
