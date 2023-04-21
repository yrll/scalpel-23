package org.sng.main.common;

import org.checkerframework.checker.units.qual.PolyUnit;
import org.sng.datamodel.Prefix;

public class Layer2Node {
    private String _nodeName;
    private Interface _interface;

    public Layer2Node(String name, Interface inf) {
        _nodeName = name;
        _interface = inf;
    }

    public Prefix getInfPrefix() {
        return _interface.getPrefix();
    }

    public Interface getInterface() {
        return _interface;
    }

    public String getDevName() {
        return _nodeName;
    }

    public boolean isConnectedLayer2Node(Layer2Node node) {
        if (_interface.hasPrefix()) {
            return _interface.getPrefix().equals(node.getInfPrefix());
        }
        return false;
    }

    public void checkInfPrefix() {
        _interface.checkIp();
    }

}
