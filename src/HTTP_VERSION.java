import java.util.HashMap;

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
    
    public static HTTP_VERSION fromString(String versionStr) {
    	for (int i=0 ; i < HTTP_VERSION.values().length; i++) {
    		if (HTTP_VERSION.values()[i].equals(versionStr)) {
    			return HTTP_VERSION.values()[i];
    		}
    	}
    	
    	return null;
    }
}