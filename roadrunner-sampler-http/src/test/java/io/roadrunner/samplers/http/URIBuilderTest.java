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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class URIBuilderTest {

    public static Stream<Arguments> uriTemplates() {
        return Stream.of(
                // a space in a path segment must be %20 (path encoding), not '+' (form encoding)
                arguments(
                        "https://${hostname}:${port}/endpoint/${path-fragment}",
                        Map.of("hostname", "localhost", "port", "80", "path-fragment", "no echo"),
                        URI.create("https://localhost:80/endpoint/no%20echo")),
                // a space in a query value is also %20
                arguments(
                        "https://example.com/search?q=${term}",
                        Map.of("term", "hello world"),
                        URI.create("https://example.com/search?q=hello%20world")),
                // non-ASCII is percent-encoded as UTF-8
                arguments(
                        "https://example.com/${path-fragment}",
                        Map.of("path-fragment", "café"),
                        URI.create("https://example.com/caf%C3%A9")),
                // per-segment: a value is a single segment, so a '/' inside it is escaped to %2F
                // and cannot break out into extra path segments
                arguments(
                        "https://example.com/files/${name}",
                        Map.of("name", "a/b c"),
                        URI.create("https://example.com/files/a%2Fb%20c")),
                // per-segment: reserved query characters in a value are escaped, not treated as structure
                arguments(
                        "https://example.com/search?q=${term}",
                        Map.of("term", "a&b=c"),
                        URI.create("https://example.com/search?q=a%26b%3Dc")),
                // a template with no placeholders is returned unchanged
                arguments("https://example.com/plain", Map.of(), URI.create("https://example.com/plain")));
    }

    @ParameterizedTest
    @MethodSource("uriTemplates")
    void replaceUriParts(String uriTemplate, Map<String, ?> values, URI expected) {
        var uri = URIBuilder.replace(uriTemplate, values);
        assertThat(uri).isEqualTo(expected);
    }
}
