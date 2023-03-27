package org.sng.main.forwardingtree;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

public class Node {
    private String _name;
    private Ip _ip;

    public Node(String name, String ip) {
        _name = name;
        _ip = Ip.parse(ip);
    }

    public Node(String name, Ip ip) {
        _name = name;
        _ip = ip;
    }

    public String getDevName() {
        return _name;
    }

    public Ip getDevIp() {
        return _ip;
    }

    public boolean ifSameDevName(String name) {
        return name.equals(_name);
    }
}
