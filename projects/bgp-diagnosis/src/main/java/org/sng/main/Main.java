package org.sng.main;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sng.main.InputData.NetworkType;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.diagnosis.VpnInstance;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

public class Main {
    public static void main(String[] args) {

        boolean ff = "32".equals("32");

        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        logger.setLevel(Level.WARNING);

        String caseType = "2.2";
        NetworkType type = NetworkType.CLOUDNET;
        if (args.length>1) {
            caseType = args[0];
            type = NetworkType.getType(args[1]);
        }
        // boolean ifMpls = type.name().equals(KeyWord.PUBLIC_VPN_NAME);

        // Set<String> reachNodes = new HashSet<>();
        // reachNodes.add("RSG1");
        boolean ifSave = true;

        // String fp = "E:/Java/IdeaProjects/scalpel-23/sse_conditions/bgp/case1.1.json";
        // BgpCondition.deserialize(fp);
        
        BgpDiagnosis diagnoser = new BgpDiagnosis(caseType, type);
        
        // VpnInstance vv = ConfigTaint.getVpnInstance("ASG1", "LTE_RAN");
        Map<String, BgpCondition> conds = BgpCondition.deserialize("E:\\Java\\IdeaProjects\\scalpel-23\\sse_conditions\\cloudnet\\case1.1.json");

        diagnoser.diagnose(null,ifSave);

        // 定位配置行
//        if (diagnoser.getNewGenerator()!=null) {
//            diagnoser.localize(ifSave, diagnoser.getNewGenerator());
//        } else {
//            diagnoser.localize(ifSave, diagnoser.getErrGenerator());
//        }
        

//        diagnoser.genIgpConstraints(null, ifSave);

        System.out.println("pause");

    }

}
