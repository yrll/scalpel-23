package org.sng.main.forwardingtree;

import org.sng.datamodel.Prefix;

public class Node {
    private String _name;
    private Prefix _ip;

    public Node(String name, String ip) {
        _name = name;
        _ip = Prefix.parse(ip);
    }

    public Node(String name, Prefix ip) {
        _name = name;
        _ip = ip;
    }

    public String getDevName() {
        return _name;
    }

    public Prefix getDevIp() {
        return _ip;
    }

    public boolean ifSameDevName(String name) {
        return name.equals(_name);
    }
}
