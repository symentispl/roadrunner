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
package io.roadrunner.cli;

import java.lang.module.ModuleDescriptor;
import java.util.Optional;

final class Version {

    // Package.getImplementationVersion() never works here: for classes in a named module the JDK
    // always constructs Package with NULL_VERSION_INFO, regardless of manifest attributes. The
    // module's own version (embedded by --module-version, see the root pom) is only reachable via
    // the module descriptor.
    static String full() {
        return Optional.ofNullable(Version.class.getModule().getDescriptor())
                .flatMap(ModuleDescriptor::rawVersion)
                .orElse("unknown");
    }

    static String[] humanReadable() {
        return new String[] {"Roadrunner, a load generator, version " + full()};
    }
}
