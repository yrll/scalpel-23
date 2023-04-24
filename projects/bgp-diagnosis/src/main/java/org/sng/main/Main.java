package org.sng.main;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.sng.main.InputData.NetworkType;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.diagnosis.VpnInstance;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

public class Main {
    public static boolean printLog = false;

    public static void main(String[] args) throws ArgumentParserException {
        String arg1 = "case";
        String arg2 = "netType";
        String arg3 = "localize";
        String arg4 = "ifSave";
        String arg5 = "ifPrint";

        ArgumentParser parser = ArgumentParsers.newFor("scalpel").build().description("Diagnosis inputs");
        parser.addArgument("-case").type(String.class).help("network case index");
        parser.addArgument("-netType").type(String.class).help("network type");
        parser.addArgument("-localize").type(boolean.class).help("fasle means only diagnosing");
        parser.addArgument("-ifSave").type(boolean.class).help("save flag");
        parser.addArgument("-ifPrint").type(boolean.class).help("log print");
        Namespace parsedArgs = parser.parseArgs(args);

        // 网络类型，case序号，标志
        String caseType = "1";
        NetworkType netType = NetworkType.IPMETRO;
        boolean ifLocalize = false;
        boolean ifSave = false;

        // 被main输入覆盖
        if (parsedArgs.get(arg1)!=null) {
            caseType = parsedArgs.get(arg1);
        }
        if (parsedArgs.get(arg2)!=null) {
            netType = NetworkType.getType(parsedArgs.get(arg2).toString());
        }
        if (parsedArgs.get(arg3)!=null) {
            ifLocalize = parsedArgs.get(arg3);
        }
        if (parsedArgs.get(arg4)!=null) {
            ifSave = parsedArgs.get(arg4);
        }

        Set<String> failedDevs = InputData.getFailedNodes(caseType, netType);


        BgpDiagnosis diagnoser = new BgpDiagnosis(caseType, netType, failedDevs);

        if (ifLocalize) {
            // 定位配置行
            if (diagnoser.getNewGenerator()!=null) {
                diagnoser.localize(ifSave, diagnoser.getNewGenerator(), diagnoser.getErrGenerator());
                diagnoser.genIgpConstraints(diagnoser.getNewGenerator().getBgpTree(), ifSave);
            } else {
                throw new IllegalArgumentException("no sse provenance info!");
            }
        } else {
            // only diagnosing
            diagnoser.diagnose(ifSave);
        }

        System.out.println("end");

    }

}
