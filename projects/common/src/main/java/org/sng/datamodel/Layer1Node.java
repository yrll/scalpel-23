package org.sng.datamodel;

//
// Copied from batfish (https://github.com/batfish/batfish)
//

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Objects;

public final class Layer1Node implements Comparable<Layer1Node> {
    private static final String PROP_HOSTNAME = "hostname";
    private static final String PROP_INTERFACE_NAME = "interfaceName";
    private final String _hostname;
    private final String _interfaceName;

    @JsonCreator
    @Nonnull
    private static Layer1Node create(@JsonProperty("hostname") @Nullable String hostname, @JsonProperty("interfaceName") @Nullable String interfaceName) {
        Preconditions.checkArgument(hostname != null, "Missing %s", "hostname");
        Preconditions.checkArgument(interfaceName != null, "Missing %s", "interfaceName");
        return new Layer1Node(hostname, interfaceName);
    }

    public Layer1Node(String hostname, String interfaceName) {
        this._hostname = hostname;
        this._interfaceName = interfaceName;
    }

    public int compareTo(Layer1Node o) {
        return Comparator.comparing(Layer1Node::getHostname).thenComparing(Layer1Node::getInterfaceName).compare(this, o);
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Layer1Node)) {
            return false;
        } else {
            Layer1Node rhs = (Layer1Node)obj;
            return this._hostname.equals(rhs._hostname) && this._interfaceName.equals(rhs._interfaceName);
        }
    }

    @JsonProperty("hostname")
    @Nonnull
    public String getHostname() {
        return this._hostname;
    }

    @JsonProperty("interfaceName")
    @Nonnull
    public String getInterfaceName() {
        return this._interfaceName;
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this._hostname, this._interfaceName});
    }

    public String toString() {
        return MoreObjects.toStringHelper(this.getClass()).add("hostname", this._hostname).add("interfaceName", this._interfaceName).toString();
    }
}

