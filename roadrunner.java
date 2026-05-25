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
///usr/bin/env jbang "$0" "$@" ; exit $?
// Roadrunner JBang launcher — always runs the latest release.
// Usage: jbang roadrunner@symentispl/roadrunner -- vm -n 100 -c 10
//
// Downloads roadrunner-jars (platform-independent Java archive) from GitHub
// Releases, caches it under ~/.jbang/cache/roadrunner/, and runs it using
// the same JDK that JBang is already using.

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

class roadrunner {

    static final String RELEASES = "https://github.com/symentispl/roadrunner/releases";

    public static void main(String[] args) throws Exception {
        String version = resolveLatestVersion();
        String tag = "v" + version;
        Path cacheDir =
                Path.of(System.getProperty("user.home"), ".jbang", "cache", "roadrunner", version);
        Path libDir = cacheDir.resolve("roadrunner-jars-" + version).resolve("lib");

        if (!Files.isDirectory(libDir)) {
            String archive = "roadrunner-jars-" + version + ".zip";
            String url = RELEASES + "/download/" + tag + "/" + archive;
            System.err.println("[roadrunner] downloading " + archive + " ...");
            Files.createDirectories(cacheDir);
            Path zipPath = cacheDir.resolve(archive);
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, zipPath, StandardCopyOption.REPLACE_EXISTING);
            }
            unzip(zipPath, cacheDir);
            Files.deleteIfExists(zipPath);
        }

        // Use JBang's own JDK so no separate Java installation is needed.
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path javaExec = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExec.toString());
        cmd.add("-Djdk.tracePinnedThreads=full");
        cmd.add("-p");
        cmd.add(libDir.toString());
        cmd.add("-m");
        cmd.add("io.roadrunner.cli/io.roadrunner.cli.Main");
        cmd.addAll(Arrays.asList(args));
        System.exit(new ProcessBuilder(cmd).inheritIO().start().waitFor());
    }

    static String resolveLatestVersion() throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) URI.create(RELEASES + "/latest").toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.connect();
        int status = conn.getResponseCode();
        String location = conn.getHeaderField("Location");
        conn.disconnect();
        if (status != 302 || location == null) {
            throw new IOException("Could not resolve latest release (HTTP " + status + ")");
        }
        // location ends in ".../releases/tag/v0.2.0"
        String tag = location.substring(location.lastIndexOf('/') + 1);
        if (!tag.startsWith("v")) throw new IOException("Unexpected tag format: " + tag);
        return tag.substring(1);
    }

    static void unzip(Path zip, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = dest.resolve(entry.getName()).normalize();
                if (!target.startsWith(dest)) throw new IOException("Bad zip entry: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
