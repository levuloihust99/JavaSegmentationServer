package jsonutils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import segmentation.RDRsegmenter;
import jsonutils.JSONArray;
import jsonutils.parser.JSONParser;
import jsonutils.parser.ParseException;
import jsonutils.JSONObject;

public class JSONSegmenter {
    private RDRsegmenter segmenter;
    private final Set<String> semgentKeys = new HashSet<>(Arrays.asList("question", "context", "text", "title"));

    public JSONSegmenter(RDRsegmenter segmenter) {
        this.segmenter = segmenter;
    }

    public Object segment(Object jsonNode) {
        Stack<TupleNode> stack = new Stack<>();
        TupleNode tupleNode = new TupleNode(null, null, jsonNode);
        stack.push(tupleNode);
        int counter = 0;
        while (!stack.isEmpty()) {
            TupleNode tuple = stack.pop();
            counter++;
            System.out.println("Counter = " + counter);
            Object parent = tuple.parent;
            Object parentKey = tuple.key;
            Object node = tuple.node;
            if (node instanceof String) {
                String stringNode = (String) node;
                if (parentKey instanceof String) {
                    assert parent instanceof JSONObject;
                    JSONObject parentAsObject = (JSONObject) parent;
                    try {
                        String parentKeyAsString = (String) parentKey;
                        if (semgentKeys.contains(parentKeyAsString)) {
                            parentAsObject.put(parentKeyAsString, this.segmenter.segmentRawString(stringNode));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
            }
            else if (node instanceof JSONArray) {
                JSONArray arrayNode = (JSONArray) node;
                for (int i = 0; i < arrayNode.size(); i++) {
                    stack.push(new TupleNode(arrayNode, i, arrayNode.get(i)));
                }
            }
            else if (node instanceof JSONObject) {
                JSONObject jsonDict = (JSONObject) node;
                Iterator<Map.Entry<Object, Object>> iterator = jsonDict.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    String key = (String) entry.getKey();
                    Object child = entry.getValue();
                    stack.push(new TupleNode(jsonDict, key, child));
                }
            }
        }
        return jsonNode;
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        JSONParser parser = new JSONParser();
        byte[] encoded = Files.readAllBytes(Paths.get("data/public_test_question.json"));
        String inputStr = new String(encoded, StandardCharsets.UTF_8);
        Object jsonRoot = parser.parse(inputStr);
        RDRsegmenter segmenter = new RDRsegmenter();
        JSONSegmenter jsonSegmenter = new JSONSegmenter(segmenter);
        Object jsonRootOut = jsonSegmenter.segment(jsonRoot);
        PrintStream outStream = new PrintStream("data/public_test_question_segmented.json");
        if (jsonRootOut instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) jsonRootOut;
            outStream.print(jsonArray.toString());
        }
        else if (jsonRootOut instanceof JSONObject) {
            JSONObject jsonDict = (JSONObject) jsonRootOut;
            outStream.print(jsonDict.toString());
        }
        outStream.close();
    }
}

class TupleNode {
    public Object parent;
    public Object key;
    public Object node;

    public TupleNode(Object parent, Object key, Object node) {
        this.parent = parent;
        this.key = key;
        this.node = node;
    }
}
