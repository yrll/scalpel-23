package org.sng.main;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpTopology;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.forwardingtree.BgpForwardingTree;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.forwardingtree.BgpForwardingTree.TreeType;
import org.sng.util.KeyWord;

public class BgpDiagnosis {
    private Generator _generator;


    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static void main(String[] args) {

        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        logger.setLevel(Level.WARNING);

        String caseType = "3.1";
        InputData inputData = new InputData();

        String projectRootPath = System.getProperty("user.dir");
        // BGP peer Info file path
        String peerInfoPath = concatFilePath(projectRootPath, "networks/provenanceInfo/peerInfo/PeerInfo"+caseType+".json");
        // Provenance Info files path
        String bgpProvRootPath = concatFilePath(projectRootPath, "networks/provenanceInfo/bgp");
        // The path to save BGP condition json files
        String conditionPath = concatFilePath(projectRootPath, "sse_conditions/case" + caseType +".json");
        
        // error trace: (errorDstDevName, errorDstPrefix) and prov files path
        // String errDstNode = "BNG30";
        // String errDstIp = "179.0.0.117/30";
        String errDstNode = inputData.getErrorDstName(caseType);
        String errDstIp = inputData.getErrorDstIp(errDstNode);
        String errBgpProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.ERROR, KeyWord.PROV_INFO_FILE_NAME)));
        String errStaticProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.ERROR, KeyWord.RELATED_STATIC_INFO_FILE)));
        // correct trace: (correctDstDevName, correctDstPrefix) and prov files path
        // String corDstNode = "BNG3";
        // String corDstIp = "179.0.0.9/30";
        String corDstNode = inputData.getCorrectDstName(caseType);
        String corDstIp = inputData.getCorrectDstIp(caseType);
        String corBgpProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.CORRECT, KeyWord.PROV_INFO_FILE_NAME)));
        String corStaticProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.CORRECT, KeyWord.RELATED_STATIC_INFO_FILE)));

        // generate the BGP topology using peer Info
        BgpTopology bgpTopology = new BgpTopology();
        bgpTopology.genBgpPeersFromJsonFile(peerInfoPath);
        // generate the error traffic forwarding tree (paths)
        System.out.println("ERROR TREE GENERATE...");
        Generator errGenerator = new Generator(errDstNode, errDstIp, bgpTopology);
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
}
