package org.sng.datamodel.isis;


import java.util.Objects;

public class IsisNode {
    private final String _devName;
    private final int _id;
    private final int _isisId;

    public static final int DIRECT = -1;

    public static final int NEW_ISIS_PROCESS = -2;

    public IsisNode(String devName, int id, int isisId) {
        _devName = devName;
        _id = id;
        _isisId = isisId;
    }

    public static IsisNode creatDirectNode(String devName){
        return new IsisNode(devName,DIRECT,DIRECT);
    }

    public static IsisNode creatNewIsisNode(String devName){
        return new IsisNode(devName,NEW_ISIS_PROCESS,NEW_ISIS_PROCESS);
    }

    public String getDevName() {
        return _devName;
    }

    public int getId() {
        return _id;
    }

    public int getIsisId() {
        return _isisId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        IsisNode node = (IsisNode) obj;
        return _devName.equals(node._devName) && _isisId == node._isisId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_devName, _isisId);
    }
}
