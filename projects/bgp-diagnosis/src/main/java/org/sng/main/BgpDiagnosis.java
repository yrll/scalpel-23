package org.sng.main;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.sng.datamodel.Prefix;
import org.sng.main.InputData.ErrorType;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.Layer2Topology;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.forwardingtree.BgpForwardingTree;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.forwardingtree.BgpForwardingTree.TreeType;
import org.sng.main.localization.Violation;
import org.sng.main.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class BgpDiagnosis {
    private Generator _generator;

    public static String cfgRootPath;
    public static Map<String, String> cfgPathMap = genCfgPathEachNode();
    

    public static Map<String, String> genCfgPathEachNode() {
        cfgPathMap = new HashMap<>();
        File rootFile = new File(cfgRootPath);
        File[] files = rootFile.listFiles();
        for (File file : files) {
            System.out.println(file.getName());
            cfgPathMap.put(file.getName().split("\\.")[0], file.getAbsolutePath());
        }
        return cfgPathMap;
    }

    public static String fromJsonToString(String filePath) {
        File file = new File(filePath);
        String jsonStr = "";
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return jsonStr;
    }

    public static void main(String[] args) {

        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        logger.setLevel(Level.WARNING);

        String caseType = "3.1";
        InputData.ErrorType type = ErrorType.BGP;
        InputData inputData = new InputData();

        // CFG root path
        BgpDiagnosis.cfgRootPath = InputData.getCfgRootPath(caseType, type);
        BgpDiagnosis.cfgPathMap = genCfgPathEachNode();
        

        // BGP peer Info file path
        String peerInfoPath = inputData.getPeerInfoPath(caseType, type);
        // Provenance Info files path
        // The path to save BGP condition json files
        String conditionPath = inputData.getConditionFilePath(caseType, type);

        // error trace: (errorDstDevName, errorDstPrefix) and prov files path
        String errDstNode = inputData.getErrorDstName(caseType, type);
        String errDstIp = inputData.getErrorDstIp(caseType, type);
        String errBgpProvFilePath = inputData.getErrorProvFilePath(caseType, type, KeyWord.PROV_INFO_FILE_NAME);
        String errStaticProvFilePath = inputData.getErrorProvFilePath(caseType, type, KeyWord.RELATED_STATIC_INFO_FILE);
        // correct trace: (correctDstDevName, correctDstPrefix) and prov files path
        String corDstNode = inputData.getCorrectDstName(caseType, type);
        String corDstIp = inputData.getCorrectDstIp(caseType, type);
        String corBgpProvFilePath = inputData.getCorrectProvFilePath(caseType, type, KeyWord.PROV_INFO_FILE_NAME);
        String corStaticProvFilePath = inputData.getCorrectProvFilePath(caseType, type, KeyWord.RELATED_STATIC_INFO_FILE);
        // Layer2 Topology
        String infInfoFilePath = inputData.getErrorProvFilePath(caseType, type, KeyWord.INTERFACE_INFO_FILE_PATH);
        Layer2Topology layer2Topology = Layer2Topology.fromJson(infInfoFilePath);

        // generate the BGP topology using peer Info
        BgpTopology bgpTopology = new BgpTopology();
        bgpTopology.genBgpPeersFromJsonFile(peerInfoPath);
        // generate the error traffic forwarding tree (paths)
        System.out.println("ERROR TREE GENERATE...");
        Generator errGenerator = new Generator(errDstNode, errDstIp, bgpTopology);
        errGenerator.setLayer2Topology(layer2Topology);
        errGenerator.serializeTreeFromJson(errStaticProvFilePath, TreeType.STATIC);
        errGenerator.serializeTreeFromJson(errBgpProvFilePath, TreeType.BGP);
        
        // generate the correct traffic forwarding tree (paths)
        System.out.println("REFERENCE TREE GENERATE...");
        Generator corGenerator = new Generator(corDstNode, corDstIp, bgpTopology);
        corGenerator.serializeTreeFromJson(corStaticProvFilePath, TreeType.STATIC);
        corGenerator.serializeTreeFromJson(corBgpProvFilePath, TreeType.BGP);
        // use the correct traffic as a reference to generate the policy-compliant "Forwarding Tree"
        BgpForwardingTree reqTree = errGenerator.genBgpTree(corGenerator.getBgpTree());
        reqTree.serializeBgpCondition(conditionPath, reqTree.genBgpConditions(bgpTopology));
        
        Map<String, BgpCondition> conds = BgpCondition.deserialize(conditionPath);
        System.out.println("pause");

    }

    public Map<String, Map<Integer, String>> getErrorLinesEachNode(String filePath, Generator generator) {
        Map<String, Map<Integer, String>> errMap = new HashMap<>();
        // 输入是violated condition文件的路径
        String jsonStr = fromJsonToString(filePath);
        String jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(KeyWord.DEV2_VIOLATE_RULES).getAsString();
        Map<String, Violation> violations = new Gson().fromJson(jsonStr, new TypeToken<Map<String, Violation>>() {}.getType());
        violations.forEach((node, vio)->{
            errMap.put(node, vio.localize(node, generator));
        });
        return errMap;
    }
}
