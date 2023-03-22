package org.sng.datamodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.SortedSet;

public class Layer1Topology {
    public static final Layer1Topology EMPTY = new Layer1Topology(ImmutableList.of());
    private static final String PROP_EDGES = "edges";
    private final ImmutableNetwork<Layer1Node, Layer1Edge> _graph;

    @JsonCreator
    @Nonnull
    private static Layer1Topology create(@Nullable @JsonProperty("edges") Iterable<Layer1Edge> edges) {
        return new Layer1Topology((Iterable)(edges != null ? edges : ImmutableSortedSet.of()));
    }

    public Layer1Topology(@Nonnull Iterable<Layer1Edge> edges) {
        MutableNetwork<Layer1Node, Layer1Edge> graph = NetworkBuilder.directed().allowsParallelEdges(false).allowsSelfLoops(false).build();
        edges.forEach((edge) -> {
            if (!edge.getNode1().equals(edge.getNode2()) && !graph.edges().contains(edge)) {
                graph.addEdge(edge.getNode1(), edge.getNode2(), edge);
            }
        });
        this._graph = ImmutableNetwork.copyOf(graph);
    }

    @JsonIgnore
    @Nonnull
    public ImmutableNetwork<Layer1Node, Layer1Edge> getGraph() {
        return this._graph;
    }

    @JsonProperty("edges")
    private SortedSet<Layer1Edge> getJsonEdges() {
        return ImmutableSortedSet.copyOf(this._graph.edges());
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            return !(obj instanceof Layer1Topology) ? false : this._graph.equals(((Layer1Topology)obj)._graph);
        }
    }

    public int hashCode() {
        return this._graph.hashCode();
    }
}
