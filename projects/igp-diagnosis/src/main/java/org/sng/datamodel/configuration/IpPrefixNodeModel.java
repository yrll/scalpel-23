package org.sng.datamodel.configuration;

import org.sng.datamodel.Prefix;

public class IpPrefixNodeModel {
    private final Integer _index;
    private final String _matchMode;
    private final Prefix _ipMask;
    private final Integer _greaterEqual;
    private final Integer _lessEqual;


    public IpPrefixNodeModel(Integer index, String matchMode, Prefix ipMask, Integer greaterEqual, Integer lessEqual) {
        _index = index;
        _matchMode = matchMode;
        _ipMask = ipMask;
        _greaterEqual = greaterEqual;
        _lessEqual = lessEqual;
    }

    public Integer getIndex() {
        return _index;
    }

    public String getMatchMode() {
        return _matchMode;
    }

    public Prefix getIpMask() {
        return _ipMask;
    }

    public Integer getGreaterEqual() {
        return _greaterEqual;
    }

    public Integer getLessEqual() {
        return _lessEqual;
    }

    public boolean matchPrefix(Prefix prefix){
        if (_greaterEqual == null && _lessEqual == null){
            return _ipMask.equals(prefix);
        }
        else {
            int greatEqual = _greaterEqual == null ? _ipMask.getPrefixLength() :_greaterEqual;
            int lessEqual = _lessEqual == null ? 32  : _lessEqual;
            if (_ipMask.containsIp(prefix.getStartIp())){
                int prefixLength = prefix.getPrefixLength();
                return prefixLength >= greatEqual && prefixLength <= lessEqual;
            }
        }
        return false;
    }
}
