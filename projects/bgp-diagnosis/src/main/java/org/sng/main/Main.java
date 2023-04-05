package org.sng.main;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sng.main.InputData.ErrorType;
import org.sng.main.util.KeyWord;

public class Main {
    public static void main(String[] args) {

        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        logger.setLevel(Level.WARNING);

        String caseType = "2.3";
        ErrorType type = ErrorType.BGP;

        Set<String> reachNodes = new HashSet<>();
        reachNodes.add("BNG1");
        
        BgpDiagnosis diagnoser = new BgpDiagnosis(caseType, type);
        diagnoser.diagnose(reachNodes, null, true);
        diagnoser.localize(true);
        System.out.println("pause");

    }

}
