package org.sng.datamodel;

//
// Copied from batfish (https://github.com/batfish/batfish)
//

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Optional;

@ParametersAreNonnullByDefault
public final class Prefix implements Comparable<Prefix>, Serializable {
    private static final LoadingCache<Prefix, Prefix> CACHE = CacheBuilder.newBuilder().softValues().maximumSize(1048576L).build(CacheLoader.from((x) -> {
        return x;
    }));
    public static final int MAX_PREFIX_LENGTH = 32;
    public static final Prefix ZERO;
    public static final Prefix MULTICAST;
    public static final int HOST_SUBNET_MAX_PREFIX_LENGTH = 29;
    private final Ip _ip;
    private final int _prefixLength;

    private static long wildcardMaskForPrefixLength(int prefixLength) {
        assert 0 <= prefixLength && prefixLength <= 32;

        return (1L << 32 - prefixLength) - 1L;
    }

    @Nonnull
    public static Prefix parse(@Nullable String text) {
        Preconditions.checkArgument(text != null, "Invalid IPv4 prefix %s", text);
        String[] parts = text.split("/");
        Preconditions.checkArgument(parts.length == 2, "Invalid prefix string: \"%s\"", text);
        Ip ip = Ip.parse(parts[0]);

        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException var5) {
            throw new IllegalArgumentException("Invalid prefix length: \"" + parts[1] + "\"", var5);
        }

        return create(ip, prefixLength);
    }

    @Nonnull
    public static Optional<Prefix> tryParse(@Nonnull String text) {
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException var2) {
            return Optional.empty();
        }
    }

    @Nonnull
    public static Prefix strict(String prefixStr) {
        Prefix prefix = parse(prefixStr);
        Preconditions.checkArgument(prefix.toString().equals(prefixStr), "Non-canonical prefix: %s", prefixStr);
        return prefix;
    }

    private Prefix(Ip ip, int prefixLength) {
        Preconditions.checkArgument(prefixLength >= 0 && prefixLength <= 32, "Invalid prefix length %s", prefixLength);
        if (ip.valid()) {
            this._ip = ip.getNetworkAddress(prefixLength);
        } else {
            this._ip = ip;
        }

        this._prefixLength = prefixLength;
    }

    public static Prefix create(Ip ip, int prefixLength) {
        Prefix p = new Prefix(ip, prefixLength);
        return (Prefix)CACHE.getUnchecked(p);
    }

    public static Prefix create(Ip address, Ip mask) {
        return create(address, mask.numSubnetBits());
    }

    public static Prefix longestCommonPrefix(Prefix p1, Prefix p2) {
        if (p1.containsPrefix(p2)) {
            return p1;
        } else if (p2.containsPrefix(p1)) {
            return p2;
        } else {
            long l1 = p1.getStartIp().asLong();
            long l2 = p2.getStartIp().asLong();
            long oneAtFirstDifferentBit = Long.highestOneBit(l1 ^ l2);
            int lengthInCommon = 31 - Long.numberOfTrailingZeros(oneAtFirstDifferentBit);
            return create(Ip.create(l1), lengthInCommon);
        }
    }

    public int compareTo(Prefix rhs) {
        if (this == rhs) {
            return 0;
        } else {
            int ret = this._ip.compareTo(rhs._ip);
            return ret != 0 ? ret : Integer.compare(this._prefixLength, rhs._prefixLength);
        }
    }

    public boolean containsIp(Ip ip) {
        long masked = ip.asLong() & ~wildcardMaskForPrefixLength(this._prefixLength);
        return masked == this._ip.asLong();
    }

    public boolean containsPrefix(Prefix prefix) {
        return this._prefixLength <= prefix._prefixLength && this.containsIp(prefix._ip);
    }

    public boolean equals(@Nullable Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Prefix)) {
            return false;
        } else {
            Prefix rhs = (Prefix)o;
            return this._ip.equals(rhs._ip) && this._prefixLength == rhs._prefixLength;
        }
    }

    @Nonnull
    public Ip getEndIp() {
        long networkEnd = this._ip.asLong() | wildcardMaskForPrefixLength(this._prefixLength);
        return Ip.create(networkEnd);
    }

    public int getPrefixLength() {
        return this._prefixLength;
    }

    @Nonnull
    public Ip getPrefixWildcard() {
        long wildcardLong = wildcardMaskForPrefixLength(this._prefixLength);
        return Ip.create(wildcardLong);
    }

    @Nonnull
    public Ip getStartIp() {
        return this._ip;
    }

    public int hashCode() {
        return 31 + 31 * Long.hashCode(this._ip.asLong()) + this._prefixLength;
    }

    public Ip getFirstHostIp() {
        if (this._prefixLength >= 31) {
            return this.getStartIp();
        } else {
            Ip subnetIp = this.getStartIp();
            return Ip.create(subnetIp.asLong() + 1L);
        }
    }

    public Ip getLastHostIp() {
        if (this._prefixLength >= 31) {
            return this.getEndIp();
        } else {
            Ip broadcastIp = this.getEndIp();
            return Ip.create(broadcastIp.asLong() - 1L);
        }
    }

    public String toString() {
        return this._ip + "/" + this._prefixLength;
    }

    private Object readResolve() throws ObjectStreamException {
        return CACHE.getUnchecked(this);
    }

    static {
        ZERO = create(Ip.ZERO, 0);
        MULTICAST = create(Ip.parse("224.0.0.0"), 4);
    }
}
