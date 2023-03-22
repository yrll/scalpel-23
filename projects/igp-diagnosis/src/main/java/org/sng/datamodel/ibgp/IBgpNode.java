package org.sng.datamodel.ibgp;

import org.sng.datamodel.Ip;

public class IBgpNode {

    private final Ip _ip;
    private final String _devName;

    public IBgpNode(Ip ip, String devName) {
        _ip = ip;
        _devName = devName;
    }


    public Ip getIp() {
        return _ip;
    }

    public String getDevName() {
        return _devName;
    }
}
