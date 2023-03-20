package org.sng.datamodel.isis;

public class IsisEdge {

    private final IsisNode _source;
    private final IsisNode _target;
    private final IsisEdgeValue _edgeValue;


    public IsisEdge(IsisNode source, IsisNode target, IsisEdgeValue edgeValue) {
        _source = source;
        _target = target;
        _edgeValue = edgeValue;
    }

    public IsisNode getSource() {
        return _source;
    }

    public IsisNode getTarget() {
        return _target;
    }

    public IsisEdgeValue getEdgeValue() {
        return _edgeValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != getClass())
            return false;
        IsisEdge edge = (IsisEdge) obj;
        return _source.equals(edge._source) && _target.equals(edge._target);
    }
}
