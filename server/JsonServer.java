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
import java.util.Random;

public class JsonServer {
    private static final String HOSTNAME = "0.0.0.0";
    private static final int PORT = 8088;
    private static final int BACKLOG = 1;
    private static final int POOL_SIZE = 100;

    public static void main(final String... args) throws IOException {
        ArrayList<RDRsegmenter> segmenterPool = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            segmenterPool.add(new RDRsegmenter());
        }
        Random rd = new Random();
        final HttpServer server = HttpServer.create(new InetSocketAddress(HOSTNAME, PORT), BACKLOG);
        server.createContext("/segment", he -> {
            RDRsegmenter segmenter = segmenterPool.get(rd.nextInt(POOL_SIZE));
            RequestHandler handler = new RequestHandler(he, segmenter);
            new Thread(handler).start();
        });
        server.start();
    }
}

class RequestHandler implements Runnable {
    private HttpExchange he;
    private RDRsegmenter segmenter;

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final int STATUS_OK = 200;

    private static final String METHOD_POST = "POST";
    private static final JSONParser jsonParser = new JSONParser();

    RequestHandler(HttpExchange he, RDRsegmenter segmenter) {
        this.he = he;
        this.segmenter = segmenter;
    }

    public void run() {
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
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            he.close();
        }
    }

    private JSONObject getRequestBody(HttpExchange he) {
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
}