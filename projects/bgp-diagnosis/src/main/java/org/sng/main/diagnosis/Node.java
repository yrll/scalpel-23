package org.sng.main.diagnosis;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.annotations.SerializedName;

public class Node {
    @SerializedName("devName")
    private String _devName;
    private transient Ip _ip;
    private transient Prefix _prefix;
    @SerializedName("ip")
    private String ipString;

    public Node(String name, String ip) {
        ipString = ip;
        _devName = name;
        _ip = Ip.parse(ip);
        _prefix = _ip.toPrefix();
    }

    public Node(String name, Ip ip) {
        ipString = ip.toPrefix().toString();
        _devName = name;
        _ip = ip;
        _prefix = _ip.toPrefix();
    }

    public Node(String name, Prefix prefix) {
        ipString = prefix.toString();
        _devName = name;
        _prefix = prefix;
        _ip = prefix.getEndIp();
    }

    public String getDevName() {
        return _devName;
    }

    public Ip getDevIp() {
        return _ip;
    }

    public boolean ifSameDevName(String name) {
        return name.equals(_devName);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Node) {
            Node node = (Node) object;
            return node.getDevName().equals(_devName) && node.getDevIp().equals(_ip);
        }
        return false;
    }
}
