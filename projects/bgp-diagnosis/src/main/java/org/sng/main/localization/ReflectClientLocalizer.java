package org.sng.main.localization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.main.common.BgpTopology;
import org.sng.main.util.KeyWord;

public class ReflectClientLocalizer implements Localizer {
    String localDevName;
    List<String> clientDevs;
    private Violation violation;
    private BgpTopology bgpTopology;

    public ReflectClientLocalizer(String localDev, List<String> clients, Violation violation, BgpTopology bgpTopology) {
        this.localDevName = localDev;
        this.clientDevs = clients;
        this.violation = violation;
        this.bgpTopology = bgpTopology;
    }
    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        Map<Integer, String> lines = new HashMap<>();
        // 行号为-1表示没有缺失该行

        clientDevs.forEach(n->{
            String peerIp = bgpTopology.getNodeIp(n).toString();
            lines.put(violation.getMissingLine(), "peer " + peerIp + " reflect-client");
        });
        return lines;
    }
    
}
