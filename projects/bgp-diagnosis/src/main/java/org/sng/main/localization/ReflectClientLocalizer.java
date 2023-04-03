package org.sng.main.localization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.main.util.KeyWord;

public class ReflectClientLocalizer implements Localizer {
    String localDevName;
    List<String> clientDevs;
    private Violation violation;

    public ReflectClientLocalizer(String localDev, List<String> clients, Violation violation) {
        this.localDevName = localDev;
        this.clientDevs = clients;
        this.violation = violation;
    }
    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        Map<Integer, String> lines = new HashMap<>();
        // 行号为-1表示没有缺失该行
        clientDevs.forEach(n->lines.put(violation.getMissingLine(), "peer " + n + " reflect-client"));

        throw new UnsupportedOperationException("Unimplemented method 'getErrorConfigLines'");
    }
    
}
