package org.sng.datamodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Comparator;
import java.util.Objects;

@ParametersAreNonnullByDefault
public final class Layer1Edge implements Comparable<Layer1Edge> {
    private static final String PROP_NODE1 = "node1";
    private static final String PROP_NODE2 = "node2";
    private final Layer1Node _node1;
    private final Layer1Node _node2;

    @JsonCreator
    @Nonnull
    private static Layer1Edge create(@JsonProperty("node1") Layer1Node node1, @JsonProperty("node2") Layer1Node node2) {
        return new Layer1Edge((Layer1Node) Objects.requireNonNull(node1), (Layer1Node)Objects.requireNonNull(node2));
    }

    public Layer1Edge(Layer1Node node1, Layer1Node node2) {
        this._node1 = node1;
        this._node2 = node2;
    }

    public Layer1Edge(@Nonnull String node1Hostname, @Nonnull String node1InterfaceName, @Nonnull String node2Hostname, @Nonnull String node2InterfaceName) {
        this(new Layer1Node(node1Hostname, node1InterfaceName), new Layer1Node(node2Hostname, node2InterfaceName));
    }

    public int compareTo(Layer1Edge o) {
        return Comparator.comparing(Layer1Edge::getNode1).thenComparing(Layer1Edge::getNode2).compare(this, o);
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Layer1Edge)) {
            return false;
        } else {
            Layer1Edge rhs = (Layer1Edge)obj;
            return this._node1.equals(rhs._node1) && this._node2.equals(rhs._node2);
        }
    }

    @JsonProperty("node1")
    @Nonnull
    public Layer1Node getNode1() {
        return this._node1;
    }

    @JsonProperty("node2")
    @Nonnull
    public Layer1Node getNode2() {
        return this._node2;
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this._node1, this._node2});
    }

    @Nonnull
    public Layer1Edge reverse() {
        return new Layer1Edge(this._node2, this._node1);
    }


    public String toString() {
        return MoreObjects.toStringHelper(this.getClass()).add("node1", this._node1).add("node2", this._node2).toString();
    }
}