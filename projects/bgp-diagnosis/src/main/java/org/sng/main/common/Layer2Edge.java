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
}
