package org.sng.main.common;

import org.sng.datamodel.Prefix;

public class Layer2Edge {
    private Layer2Node _node1;
    private Layer2Node _node2;

    private Prefix _ipv4Prefix;

    public Layer2Edge(Layer2Node node1, Layer2Node node2) {
        _node1 = node1;
        _node2 = node2;
        _ipv4Prefix = node1.getInfPrefix();
    }

    public String getNode1Name() {
        return _node1.getDevName();
    }

    public String getNode2Name() {
        return _node2.getDevName();
    }

    public Prefix getInfPrefix() {
        return _ipv4Prefix;
    }

    public String getAnotherDevName(String name) {
        if (name.equals(_node1.getDevName())) {
            return _node2.getDevName();
        } else if (name.equals(_node2.getDevName())) {
            return _node1.getDevName();
        } else {
            return null;
        }
    }
}
