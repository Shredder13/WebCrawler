/**
 *
 */
public enum HTTP_CODE {
    C200_OK("200 OK"),
    ERR_400_BAD_REQUEST("400 Bad Request"),
    ERR_403_FORBIDDEN("403 Forbidden"),
    ERR_404_NOT_FOUND("404 Not Found"),
    ERR_500_INTERNAL_SERVER_ERROR("500 Internal Server Error"),
    ERR_501_NOT_IMPLEMENTED("501 Not Implemented"),
    
    //This is special case, when HTTPReqErr should just deliver "disconnect" message to ListenTask,
    //and no response should be sent.
    NO_RESPONSE("NO-RESPONSE");

    private final String code;

    HTTP_CODE(String s) {
        code = s;
    }

    public String toString() {
        return this.code;
    }
}
