/**
 *
 */
public enum HTTP_METHOD {
    GET("GET"),
    POST("POST"),
    HEAD("HEAD"),
    TRACE("TRACE"),
    OPTIONS("OPTIONS");

    private final String code;

    HTTP_METHOD(String s) {
        code = s;
    }
    public String toString() {
        return this.code;
    }

    public String methodsList() {
        StringBuilder methods = new StringBuilder();
        for (HTTP_METHOD m: HTTP_METHOD.values()) {
            methods.append(m.toString());
            methods.append(",");
        }
        methods.delete(methods.length() - 1 , methods.length());
        return methods.toString();
    }
}