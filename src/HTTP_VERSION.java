/**
 * Types of possible HTTP versions
 */
public enum HTTP_VERSION {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1");

    private final String version;

    HTTP_VERSION(final String s) {
        this.version = s;
    }

    public String toString() {
        return this.version;
    }
}