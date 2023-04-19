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
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;

public class Ip implements Comparable<Ip>, Serializable {
    private static final LoadingCache<Ip, Ip> CACHE = CacheBuilder.newBuilder().softValues().maximumSize(1048576L).build(CacheLoader.from((x) -> {
        return x;
    }));
    public static final Ip AUTO = create(-1L);
    public static final Ip FIRST_CLASS_A_PRIVATE_IP = parse("10.0.0.0");
    public static final Ip FIRST_CLASS_B_PRIVATE_IP = parse("172.16.0.0");
    public static final Ip FIRST_CLASS_C_PRIVATE_IP = parse("192.168.0.0");
    public static final Ip FIRST_CLASS_E_EXPERIMENTAL_IP = parse("240.0.0.0");
    public static final Ip FIRST_MULTICAST_IP = parse("224.0.0.0");
    public static final Ip MAX = create(4294967295L);
    public static final Ip ZERO = create(0L);

    @SerializedName("ip")
    private final long _ip;


    public static boolean getBitAtPosition(Ip ip, int position) {
        return getBitAtPosition(ip.asLong(), position);
    }

    public static boolean getBitAtPosition(long bits, int position) {
        Preconditions.checkArgument(position >= 0 && position < 32, "Invalid bit position %s", position);
        return (bits & (long)(1 << 31 - position)) != 0L;
    }

    public static boolean isIpv4Addr(String addr) {
        String[] addrArray = addr.split("\\.");
        if (addrArray.length != 4) {
            if ((addr.charAt(0) >= 'a' && addr.charAt(0) <= 'z') || addr.charAt(0) >= 'A' && addr.charAt(0) <= 'Z') {
                String[] tail = addr.split("\\(");
                if (tail.length == 2) {
                    String[] longStrParts = tail[1].split("l");
                    if (longStrParts.length == 2) {
                        String longStr = longStrParts[0];
                        return true;
                    }
                }
            }
            return false;
        } else {
            long num = 0L;
            try {
                for(int i = 0; i < 4; ++i) {
                    long segment = Long.parseLong(addrArray[i]);
                    if (!(0L <= segment && segment <= 255L)) {
                        return false;
                    }
                    num = (num << 8) + segment;
                }

                return true;
            } catch (NumberFormatException var7) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + addr, var7);
            }
        }
    }

    private static long ipStrToLong(String addr) {
        String[] addrArray = addr.split("\\.");
        if (addrArray.length != 4) {
            if (addr.startsWith("INVALID_IP") || addr.startsWith("AUTO/NONE")) {
                String[] tail = addr.split("\\(");
                if (tail.length == 2) {
                    String[] longStrParts = tail[1].split("l");
                    if (longStrParts.length == 2) {
                        String longStr = longStrParts[0];
                        return Long.parseLong(longStr);
                    }
                }
            }

            throw new IllegalArgumentException("Invalid IPv4 address: " + addr);
        } else {
            long num = 0L;

            try {
                for(int i = 0; i < 4; ++i) {
                    long segment = Long.parseLong(addrArray[i]);
                    Preconditions.checkArgument(0L <= segment && segment <= 255L, "Invalid IPv4 address: %s. %s is an invalid octet", addr, addrArray[i]);
                    num = (num << 8) + segment;
                }

                return num;
            } catch (NumberFormatException var7) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + addr, var7);
            }
        }
    }

    static long numSubnetBitsToSubnetLong(int numBits) {
        return ~(4294967295L >> numBits) & 4294967295L;
    }

    public static Ip numSubnetBitsToSubnetMask(int numBits) {
        long mask = numSubnetBitsToSubnetLong(numBits);
        return create(mask);
    }

    @Nonnull
    public static Optional<Ip> tryParse(@Nonnull String text) {
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException var2) {
            return Optional.empty();
        }
    }

    private Ip(long ipAsLong) {
        this._ip = ipAsLong;
    }

    public static Ip parse(String ipAsString) {
        if (ipAsString.contains("/")) {
            String[] ips = ipAsString.split("/");
            if (!ips[1].equals("32")) {
                return null;
            }
            ipAsString = ips[0];
        }
        return create(ipStrToLong(ipAsString));
    }

    public static Ip create(long ipAsLong) {
        Preconditions.checkArgument(ipAsLong <= 4294967295L, "Invalid IP value: %s", ipAsLong);
        Ip ip = new Ip(ipAsLong);
        return (Ip)CACHE.getUnchecked(ip);
    }

    public long asLong() {
        return this._ip;
    }

    public int compareTo(Ip rhs) {
        return Long.compare(this._ip, rhs._ip);
    }

    public boolean equals(@Nullable Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Ip)) {
            return false;
        } else {
            Ip rhs = (Ip)o;
            return this._ip == rhs._ip;
        }
    }

    public Ip getClassMask() {
        long firstOctet = this._ip >> 24;
        if (firstOctet <= 127L) {
            return create(4278190080L);
        } else if (firstOctet <= 191L) {
            return create(4294901760L);
        } else if (firstOctet <= 223L) {
            return create(4294967040L);
        } else {
            throw new IllegalArgumentException("Cannot compute classmask");
        }
    }

    public int getClassNetworkSize() {
        long firstOctet = this._ip >> 24;
        if (firstOctet <= 127L) {
            return 8;
        } else if (firstOctet <= 191L) {
            return 16;
        } else {
            return firstOctet <= 223L ? 24 : -1;
        }
    }

    public Ip getNetworkAddress(int subnetBits) {
        long masked = this._ip & numSubnetBitsToSubnetLong(subnetBits);
        return masked == this._ip ? this : create(masked);
    }

    public int hashCode() {
        return Long.hashCode(this._ip);
    }

    public Ip inverted() {
        long invertedLong = ~this._ip & 4294967295L;
        return create(invertedLong);
    }

    public int numSubnetBits() {
        int numTrailingZeros = Long.numberOfTrailingZeros(this._ip);
        return numTrailingZeros > 32 ? 0 : 32 - numTrailingZeros;
    }

    public boolean isValidNetmask1sLeading() {
        int numTrailingZeros = Math.min(Long.numberOfTrailingZeros(this._ip), 32);
        return this._ip >> numTrailingZeros == MAX.asLong() >> numTrailingZeros;
    }

    public String toString() {
        if (!this.valid()) {
            return this._ip == -1L ? "AUTO/NONE(-1l)" : "INVALID_IP(" + this._ip + "l)";
        } else {
            return (this._ip >> 24 & 255L) + "." + (this._ip >> 16 & 255L) + "." + (this._ip >> 8 & 255L) + "." + (this._ip & 255L);
        }
    }

    public boolean valid() {
        return 0L <= this._ip && this._ip <= 4294967295L;
    }

    public Prefix toPrefix() {
        return Prefix.create(this, 32);
    }

    private Object readResolve() throws ObjectStreamException {
        return CACHE.getUnchecked(this);
    }

    
}
