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

import java.net.InetSocketAddress;
import picocli.CommandLine;

public class InetSocketAddressConverter implements CommandLine.ITypeConverter<InetSocketAddress> {
    @Override
    public InetSocketAddress convert(String value) throws Exception {
        int indexOf = value.lastIndexOf(":");
        if (indexOf == -1) {
            return new InetSocketAddress(value, 80);
        } else {
            int port = Integer.valueOf(value.substring(indexOf + 1));
            String hostname = value.substring(0, indexOf);
            return new InetSocketAddress(hostname, port);
        }
    }
}
