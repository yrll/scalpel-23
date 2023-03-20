package org.sng.datamodel.isis;

import com.google.common.graph.*;

import java.util.List;

public class IsisTopology {

    private ValueGraph<IsisNode,IsisEdgeValue> _graph;

    public IsisTopology(ValueGraph<IsisNode,IsisEdgeValue> graph){
        _graph = ImmutableValueGraph.copyOf(graph);
    }

    public static IsisTopology creat(List<IsisNode> nodes, List<IsisEdge> edges){
        MutableValueGraph graph = ValueGraphBuilder.directed().allowsSelfLoops(false).build();
        if (nodes != null){
            nodes.forEach(graph::addNode);
        }
        if (edges != null){
            edges.forEach(isisEdge -> {
                graph.putEdgeValue(isisEdge.getSource(),isisEdge.getTarget(),isisEdge.getEdgeValue());
            });
        }
        return new IsisTopology(graph);
    }

    public ValueGraph<IsisNode,IsisEdgeValue> getGraph() {
        return _graph;
    }
}
