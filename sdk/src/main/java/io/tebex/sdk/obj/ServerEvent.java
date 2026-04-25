package io.tebex.sdk.obj;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Getter
public class ServerEvent {
    @SerializedName("username_id")
    private final String uuid;
    @SerializedName("event_type")
    private final String eventType;
    @SerializedName("event_date")
    private final String eventDate;
    private final String username;
    private final String ip;

    public ServerEvent(String uuid, String username, String ip, EnumServerEventType eventType) {
        this.uuid = uuid;
        this.username = username;
        this.ip = anonymizeIp(ip);
        this.eventType = eventType.getName();
        this.eventDate = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    /**
     * Anonymizes the last octet in a given IP.
     *
     * @param ipIn The full IP address, ex. 192.168.1.100
     * @return An anonymized IP, ex. 192.168.1.x
     */
    private String anonymizeIp(String ipIn) {
        String normalizedIp = normalizeIp(ipIn);

        int lastOctetStart = normalizedIp.lastIndexOf(".");
        if (lastOctetStart == -1) {
            return "0.0.0.x";
        }
        return normalizedIp.substring(0, lastOctetStart) + ".x";
    }

    /**
     * Normalizes socket-style address inputs to plain IPv4 where possible.
     *
     * Fabric may provide values such as "/127.0.0.1:25565" or "hostname/127.0.0.1:25565".
     */
    private String normalizeIp(String ipIn) {
        if (ipIn == null) {
            return "";
        }

        String ip = ipIn.trim();
        if (ip.isEmpty()) {
            return "";
        }

        int slashIndex = ip.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < ip.length()) {
            ip = ip.substring(slashIndex + 1);
        }

        if (ip.startsWith("[")) {
            int closeBracket = ip.indexOf(']');
            if (closeBracket > 0) {
                ip = ip.substring(1, closeBracket);
            }
        } else {
            int firstColon = ip.indexOf(':');
            int lastColon = ip.lastIndexOf(':');
            if (firstColon == lastColon && firstColon > 0 && firstColon + 1 < ip.length() && isDigits(ip.substring(firstColon + 1))) {
                ip = ip.substring(0, firstColon);
            }
        }

        return ip;
    }

    private boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
