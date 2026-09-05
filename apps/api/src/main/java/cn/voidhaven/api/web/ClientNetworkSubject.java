package cn.voidhaven.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientNetworkSubject {
    private final boolean trustProxyHeaders;
    private final List<Network> trustedProxies;

    public ClientNetworkSubject(
            @Value("${voidhaven.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${voidhaven.trusted-proxy-networks:127.0.0.1/32,::1/128}") String trustedProxyNetworks) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxies = Arrays.stream(trustedProxyNetworks.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(Network::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String direct = numericAddress(request.getRemoteAddr());
        if (!trustProxyHeaders || trustedProxies.stream().noneMatch(network -> network.contains(direct))) return direct;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.contains(",")) return direct;
        try { return numericAddress(forwarded.trim()); }
        catch (IllegalArgumentException ignored) { return direct; }
    }

    private static String numericAddress(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")) throw new IllegalArgumentException("Network address must be numeric");
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid network address", error); }
    }

    private record Network(byte[] address, int prefixLength) {
        static Network parse(String value) {
            String[] parts = value.split("/", 2);
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int bits = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
                if (bits < 0 || bits > address.length * 8) throw new IllegalArgumentException("Invalid prefix");
                return new Network(address, bits);
            } catch (Exception error) { throw new IllegalStateException("Invalid trusted proxy network: " + value, error); }
        }

        boolean contains(String value) {
            try {
                byte[] candidate = InetAddress.getByName(value).getAddress();
                if (candidate.length != address.length) return false;
                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;
                for (int index = 0; index < fullBytes; index++) if (candidate[index] != address[index]) return false;
                if (remainingBits == 0) return true;
                int mask = 0xff << (8 - remainingBits);
                return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
            } catch (Exception error) { return false; }
        }
    }
}
