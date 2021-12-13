package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import jsonutils.JSONObject;
import jsonutils.parser.JSONParser;
import segmentation.RDRsegmenter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonServer {
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 8080;
    private static final int BACKLOG = 1;

    private static final String HEADER_ALLOW = "Allow";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final int STATUS_OK = 200;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;

    private static final int NO_RESPONSE_LENGTH = -1;

    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String ALLOWED_METHODS = METHOD_GET + "," + METHOD_OPTIONS + "," + METHOD_POST;
    private static final JSONParser jsonParser = new JSONParser();

    public static void main(final String... args) throws IOException {
        RDRsegmenter segmenter = new RDRsegmenter();
        final HttpServer server = HttpServer.create(new InetSocketAddress(HOSTNAME, PORT), BACKLOG);
        server.createContext("/segment", he -> {
            try {
                final Headers headers = he.getResponseHeaders();
                final String requestMethod = he.getRequestMethod().toUpperCase();
                assert requestMethod == METHOD_POST;
                final JSONObject reqBody = getRequestBody(he);
                Object sentence = reqBody.get("sentence");
                String sentenceStr = (String) sentence;
                JSONObject sentenceOut = new JSONObject();
                sentenceOut.put("sentence", segmenter.segmentRawString(sentenceStr));

                byte[] rawRespBody = sentenceOut.toString().getBytes(CHARSET);
                headers.set(HEADER_CONTENT_TYPE, String.format("application/json; charset=%s", CHARSET));
                he.sendResponseHeaders(STATUS_OK, rawRespBody.length);
                he.getResponseBody().write(rawRespBody);
            } finally {
                he.close();
            }
        });
        server.start();
    }

    private static Map<String, List<String>> getRequestParameters(final URI requestUri) {
        final Map<String, List<String>> requestParameters = new LinkedHashMap<>();
        final String requestQuery = requestUri.getRawQuery();
        if (requestQuery != null) {
            final String[] rawRequestParameters = requestQuery.split("[&;]", -1);
            for (final String rawRequestParameter : rawRequestParameters) {
                final String[] requestParameter = rawRequestParameter.split("=", 2);
                final String requestParameterName = decodeUrlComponent(requestParameter[0]);
                requestParameters.putIfAbsent(requestParameterName, new ArrayList<>());
                final String requestParameterValue = requestParameter.length > 1 ? decodeUrlComponent(requestParameter[1]) : null;
                requestParameters.get(requestParameterName).add(requestParameterValue);
            }
        }
        return requestParameters;
    }

    private static JSONObject getRequestBody(HttpExchange he) {
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(he.getRequestBody(), "utf-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        BufferedReader br = new BufferedReader(isr);

        int b;
        StringBuilder buf = new StringBuilder();
        try {
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }
    
            br.close();
            isr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Object reqBody = null;
        try {
            reqBody = jsonParser.parse(buf.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        assert reqBody instanceof JSONObject;
        JSONObject reqBodyAsJSONObject = (JSONObject) reqBody;
        return reqBodyAsJSONObject;
    }

    private static String decodeUrlComponent(final String urlComponent) {
        try {
            return URLDecoder.decode(urlComponent, CHARSET.name());
        } catch (final UnsupportedEncodingException ex) {
            throw new InternalError(ex);
        }
    }
}