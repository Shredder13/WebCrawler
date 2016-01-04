/**
 *
 */
public enum CONTENT_TYPE {
    TEXT_HTML("text/html"),
    IMAGE("image"), ICON("icon"),
    APP_OCTSTREAM("application/octet-stream"),
    MSG_HTTP("message/http");

    private final String type;

    CONTENT_TYPE(final String s) {
        this.type = s;
    }

    public String toString() {
        return this.type;
    }

}
