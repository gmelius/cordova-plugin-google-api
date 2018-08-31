package nl.xservices.plugins;

import java.util.Map;

/**
 * Created by Raphael on 17.04.18.
 */

public class BatchRequestPojo {
    private String requestMethod;
    private String requestUrl;
    private String jsonObject;
    private Map<String,Object> urlParams;

    public BatchRequestPojo(String requestMethod, String requestUrl, String jsonObject, Map<String, Object> urlParams) {
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
        this.jsonObject = jsonObject;
        this.urlParams = urlParams;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public Map<String, Object> getUrlParams() {
        return urlParams;
    }

    public String getJsonObject() {
        return jsonObject;
    }
}
