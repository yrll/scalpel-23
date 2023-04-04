package org.sng.datamodel.isis;

public class IsisEdgeValue {

    private final String _srcPhyIf;
    private final String _dstPhyIf;
    private final Integer _cost;


    public IsisEdgeValue(String srcPhyIf, String dstPhyIf, Integer cost) {
        _srcPhyIf = srcPhyIf;
        _dstPhyIf = dstPhyIf;
        _cost = cost;
    }

    public String getSrcPhyIf() {
        return _srcPhyIf;
    }

    public String getDstPhyIf() {
        return _dstPhyIf;
    }

    public Integer getCost() {
        return _cost;
    }
}
