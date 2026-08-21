package java.net.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

/**
 * Android 兼容实现：替代 Java 11 标准库的 java.net.http.HttpHeaders。
 * 仅实现 HMCLCore 用到的 firstValue / firstValueAsLong / map 能力。
 */
public final class HttpHeaders {

    private final Map<String, List<String>> headers;

    private HttpHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public static HttpHeaders of(Map<String, List<String>> headers, BiPredicate<String, String> filter) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name == null || values == null) continue;
            boolean keep = false;
            for (String v : values) {
                if (filter.test(name, v)) {
                    keep = true;
                    break;
                }
            }
            if (keep) {
                map.put(name.toLowerCase(), List.copyOf(values));
            }
        }
        return new HttpHeaders(map);
    }

    public Optional<String> firstValue(String name) {
        List<String> values = headers.get(name.toLowerCase());
        if (values != null && !values.isEmpty()) {
            return Optional.of(values.get(0));
        }
        return Optional.empty();
    }

    public OptionalLong firstValueAsLong(String name) {
        Optional<String> v = firstValue(name);
        if (v.isPresent()) {
            try {
                return OptionalLong.of(Long.parseLong(v.get()));
            } catch (NumberFormatException ignored) {
            }
        }
        return OptionalLong.empty();
    }

    public Map<String, List<String>> map() {
        return Collections.unmodifiableMap(headers);
    }
}
