package org.sng.main.forwardingtree;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.sng.datamodel.Prefix;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;

public class Generator {

    private static String UPT_TABLE = "updateTable";
    private static String CONVERGE_TABLE = "convergeInfo";
    private static String STATIC_INFO = "staticRouteInfo";

    private String _dstDevName;
    private Prefix _dstPrefix;
    private ForwardingTree _oldForwardingTree;
    private ForwardingTree _oldBGPTree;
    private ForwardingTree _oldStaticTree;
    private List<Node> _allNodes;
    private Map<Node, Node> _cfgPeerMap;

    public Generator() {

    }

    public Generator(String nodeName, String prefix) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
        // _oldBGPTree = new ForwardingTree();
        // _oldStaticTree = new ForwardingTree();
    }

    public static ForwardingTree serializeTreeFromJsonFile(String path, TreeType type, String ip) {
        File file = new File(path);
        String jsonStr;
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
            switch(type) {
                case BGP: {
                    // get BGP RIB
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(UPT_TABLE).getAsJsonObject();
                    return ForwardingTree.serializeBgpTreeFromProvJson(jsonObject, ip);
                }
                case STATIC: {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
                    return ForwardingTree.serializeStaticTreeFromProvJson(jsonObject, ip);
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ForwardingTree();
    }

    public void serializeTreeFromJson(String path, TreeType type) {
        File file = new File(path);
        String jsonStr;
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
            switch(type) {
                case BGP: {
                    // get BGP RIB
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(UPT_TABLE).getAsJsonObject();
                    _oldBGPTree = ForwardingTree.serializeBgpTreeFromProvJson(jsonObject, _dstPrefix.toString());
                    break;
                }
                case STATIC: {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
                    _oldStaticTree = ForwardingTree.serializeStaticTreeFromProvJson(jsonObject, _dstPrefix.toString());
                    break;
                } 
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        
        
        System.out.println("pause");
        
    }


    public void setBGPForwardingTree(ForwardingTree tree) {
        _oldBGPTree = tree;
    }

    public void setStaticForwardingTree(ForwardingTree tree) {
        _oldStaticTree = tree;
    }

    public ForwardingTree getNewBGPForwardingTree (ForwardingTree referenceTree) {

        return new ForwardingTree();
    }


}
