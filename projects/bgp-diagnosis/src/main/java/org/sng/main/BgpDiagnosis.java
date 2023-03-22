package org.sng.main;

import org.sng.main.forwardingtree.ForwardingTree;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.forwardingtree.KeyWord;
import org.sng.main.forwardingtree.TreeType;

public class BgpDiagnosis {
    private Generator _generator;

    public BgpDiagnosis() {
        _generator = new Generator();
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static void main(String[] args) {
        String rootPath = "/home/yrl/scalpel/Provenance/BGP Cases";
        String caseType = "case2.3";
        String errorDstNode = "BNG3";
        String errorDstIp = "179.0.0.8/30";

        // generate error traffic path
        String pathBgp = BgpDiagnosis.concatFilePath(rootPath, 
                            BgpDiagnosis.concatFilePath(caseType, 
                                BgpDiagnosis.concatFilePath(KeyWord.ERROR, KeyWord.PROV_INFO_FILE_NAME)));
        String pathStatic = BgpDiagnosis.concatFilePath(rootPath, 
                                BgpDiagnosis.concatFilePath(caseType, 
                                    BgpDiagnosis.concatFilePath(KeyWord.ERROR, KeyWord.RELATED_STATIC_INFO_FILE)));
        
        Generator generator = new Generator(errorDstNode, errorDstIp);
        generator.serializeTreeFromJson(pathStatic, TreeType.STATIC);
        generator.serializeTreeFromJson(pathBgp, TreeType.BGP);
        System.out.println("pause");

        // use the correct traffic as a reference to generate policy-compliant FT
        String corDstNode = "BNG3";
        String corDstIp = "179.0.0.8/30";
        String pathBgpCorrect = BgpDiagnosis.concatFilePath(rootPath, 
                            BgpDiagnosis.concatFilePath(caseType, 
                                BgpDiagnosis.concatFilePath(KeyWord.ERROR, KeyWord.PROV_INFO_FILE_NAME)));
        String pathStaticCorrect = BgpDiagnosis.concatFilePath(rootPath, 
                                BgpDiagnosis.concatFilePath(caseType, 
                                    BgpDiagnosis.concatFilePath(KeyWord.ERROR, KeyWord.RELATED_STATIC_INFO_FILE)));
        ForwardingTree refBgpForwardingTree = Generator.serializeTreeFromJsonFile(pathBgpCorrect, TreeType.BGP, errorDstIp);
        ForwardingTree reqBgpForwardingTree = generator.getNewBGPForwardingTree(refBgpForwardingTree);
        

    }
}
