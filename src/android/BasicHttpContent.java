package nl.xservices.plugins;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.json.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class BasicHttpContent extends AbstractHttpContent {
    private final String data;

    protected BasicHttpContent(String data) {
        super(Json.MEDIA_TYPE);
        this.data = data;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        out.write(data.getBytes(Charset.forName("UTF-8")));
    }
}
