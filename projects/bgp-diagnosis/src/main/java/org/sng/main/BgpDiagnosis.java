package org.sng.main;

import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpTopology;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.forwardingtree.ForwardingTree;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.forwardingtree.ForwardingTree.TreeType;
import org.sng.util.KeyWord;

public class BgpDiagnosis {
    private Generator _generator;


    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static void main(String[] args) {

        String caseType = "2.3";

        String projectRootPath = System.getProperty("user.dir");
        // BGP peer Info file path
        String peerInfoPath = concatFilePath(projectRootPath, "networks/provenanceInfo/peerInfo/PeerInfo"+caseType+".json");
        // Provenance Info files path
        String bgpProvRootPath = concatFilePath(projectRootPath, "networks/provenanceInfo/bgp");
        // The path to save BGP condition json files
        String conditionPath = concatFilePath(projectRootPath, "sse_conditions/case" + caseType +".json");
        
        // error trace: (errorDstDevName, errorDstPrefix) and prov files path
        String errDstNode = "BR4";
        String errDstIp = "209.0.0.12/30";
        String errBgpProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.ERROR, KeyWord.PROV_INFO_FILE_NAME)));
        String errStaticProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.ERROR, KeyWord.RELATED_STATIC_INFO_FILE)));
        // correct trace: (correctDstDevName, correctDstPrefix) and prov files path
        String corDstNode = "BR3";
        String corDstIp = "209.0.0.8/30";
        String corBgpProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.CORRECT, KeyWord.PROV_INFO_FILE_NAME)));
        String corStaticProvFilePath = concatFilePath(bgpProvRootPath, concatFilePath("case"+caseType, concatFilePath(KeyWord.CORRECT, KeyWord.RELATED_STATIC_INFO_FILE)));

        // generate the BGP topology using peer Info
        BgpTopology bgpTopology = new BgpTopology();
        bgpTopology.genBgpPeersFromJsonFile(peerInfoPath);
        // generate the error traffic forwarding tree (paths)
        System.out.println("errTree generate");
        Generator generator = new Generator(errDstNode, errDstIp, bgpTopology);
        generator.serializeTreeFromJson(errStaticProvFilePath, TreeType.STATIC);
        generator.serializeTreeFromJson(errBgpProvFilePath, TreeType.BGP);
        // generate the correct traffic forwarding tree (paths)
        System.out.println("refTree generate...");
        Generator corGenerator = new Generator(corDstNode, corDstIp, bgpTopology);
        corGenerator.serializeTreeFromJson(corStaticProvFilePath, TreeType.STATIC);
        corGenerator.serializeTreeFromJson(corBgpProvFilePath, TreeType.BGP);
        // use the correct traffic as a reference to generate the policy-compliant "Forwarding Tree"
        ForwardingTree reqTree = generator.getNewBGPForwardingTree(corGenerator.getBgpTree(), bgpTopology.getPeerTable());
        reqTree.serializeBgpCondition(conditionPath, reqTree.genBgpConditions(bgpTopology));
        
        Map<String, BgpCondition> conds = BgpCondition.deserialize(conditionPath);
        System.out.println("pause");

    }
}
