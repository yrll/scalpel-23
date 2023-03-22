package org.sng.main.forwardingtree;

import java.util.List;

import org.sng.datamodel.Prefix;

public class IBGPPeer{

    private enum ClientType {
        HEAD_IS_CLIENT,
        TAIL_IS_CLIENT,
        NONE_CLIENT
    }

    private Node _head;
    private Node _tail;
    private String _vrfName;
    private ClientType _type;
    private boolean _nextHopSelf;

    public IBGPPeer(Node head, Node tail, String vrf, ClientType type) {
        _head = head;
        _tail = tail;
        _vrfName = vrf;
        _type = type;
        _nextHopSelf = false;
    }

    public IBGPPeer(Node head, Node tail, String vrf, ClientType type, boolean nextSelf) {
        _head = head;
        _tail = tail;
        _vrfName = vrf;
        _type = type;
        _nextHopSelf = nextSelf;
    }




    

    
    
}
