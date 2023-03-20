package org.sng.main.forwardingtree;

import java.io.File;

import org.sng.datamodel.Prefix;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;

public class Generator {
    private String _dstDevName;
    private Prefix _dstPrefix;
    private ForwardingTree _oldForwardingTree;
    private ForwardingTree _oldBGPTree;
    private ForwardingTree _oldStaticTree;



    public Generator(String nodeName, String prefix) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
    }

    public static ForwardingTree serializeBGPTreeFromJson(String path) {
        // File file = new File(path);
        // String jsonStr =  FileUtils.readFileToString(file,"UTF-8");
        // return com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
    }

    public static ForwardingTree serializeStaticTreeFromJson(String path) {

    }

    public void setBGPForwardingTree(ForwardingTree tree) {
        _oldBGPTree = tree;
    }

    public void setStaticForwardingTree(ForwardingTree tree) {
        _oldStaticTree = tree;
    }

    public ForwardingTree getNewBGPForwardingTree ()

}
