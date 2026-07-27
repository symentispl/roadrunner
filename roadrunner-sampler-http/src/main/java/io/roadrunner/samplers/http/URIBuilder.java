/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.samplers.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;

final class URIBuilder {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private URIBuilder() {}

    /**
     * Substitutes {@code ${...}} placeholders in {@code uriTemplate} with {@code values}, encoding
     * each value as a single URI segment before substitution so the surrounding template's URI
     * structure is preserved.
     * <p>
     * Each value is percent-encoded per RFC 3986: everything except the unreserved set
     * ({@code A-Z a-z 0-9 - . _ ~}) is escaped as UTF-8 percent-octets. This is stricter than a
     * component encoder and is safe in any component — a space becomes {@code %20} (not {@code +}),
     * non-ASCII is UTF-8 encoded ({@code é} -> {@code %C3%A9}), and reserved characters that would
     * otherwise change the URI's shape are escaped too ({@code /} -> {@code %2F}, {@code &} ->
     * {@code %26}), so an injected value can never span a path segment or query boundary.
     */
    public static URI replace(String uriTemplate, Map<String, ?> values) {
        var encoded = new HashMap<String, String>();
        values.forEach((key, value) -> encoded.put(key, encodeSegment(String.valueOf(value))));
        return URI.create(StringSubstitutor.replace(uriTemplate, encoded));
    }

    private static String encodeSegment(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length);
        for (var raw : bytes) {
            var c = raw & 0xFF;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '.'
                    || c == '_'
                    || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
            }
        }
        return sb.toString();
    }
}
