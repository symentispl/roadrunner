#!/usr/bin/env bash
#
# Copyright 2024 Symentis.pl
# <p>
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# <p>
# http://www.apache.org/licenses/LICENSE-2.0
# <p>
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

# Usage: etc/check-docs-commands.sh [roadrunner-executable [source ...]]
# By default, check the site and README using the distribution built by Maven.
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
python3 - "$root" "$@" <<'PY'
import pathlib
import re
import shlex
import subprocess
import sys

root = pathlib.Path(sys.argv[1])
if len(sys.argv) > 2:
    executable = sys.argv[2]
else:
    binaries = list(root.glob(
        "roadrunner-app/target/jreleaser/assemble/roadrunner/jlink/"
        "work-*/roadrunner-app-*/bin/roadrunner"
    ))
    if len(binaries) != 1:
        sys.exit("Expected one built roadrunner distribution; run ./mvnw verify first.")
    executable = str(binaries[0])

sources = (
    [pathlib.Path(source) for source in sys.argv[3:]]
    if len(sys.argv) > 3
    else sorted((root / "site").rglob("*.adoc")) + sorted(root.glob("README*"))
)
command = re.compile(r"^\s*(?:\$\s+)?(?:\S*/)?roadrunner(?:\s+(.*))?$")
jbang = re.compile(r"^\s*(?:\$\s+)?jbang\s+roadrunner@\S+\s+--\s+(.*)$")
# Synopses are checked too, with representative values instead of shell redirections.
placeholders = {
    "<sampler>": "vm",
    "<concurrency>": "1",
    "<requests>": "1",
    "<ms>": "1",
    "<url>": "http://localhost:8080/",
    "<expression>": 'GET("http://localhost:8080/")',
    "<cypher>": 'query("RETURN 1")',
}
checked = failed = 0
for source in sources:
    lines = source.read_text().splitlines()
    index = 0
    while index < len(lines):
        line_number = index + 1
        match = command.match(lines[index]) or jbang.match(lines[index])
        index += 1
        if not match:
            continue
        text = match.group(1) or ""
        location = f"{source}:{line_number}"
        checked += 1
        try:
            while text.rstrip().endswith("\\"):
                if index == len(lines):
                    raise ValueError("unterminated command continuation")
                text = text.rstrip()[:-1] + " " + lines[index].strip()
                index += 1
            args = [
                placeholders.get(arg, arg)
                for arg in shlex.split(text)
                if arg not in ("[OPTIONS]", "[SAMPLER_OPTIONS]")
            ]
            if any(re.search(r"<[^>]+>", arg) for arg in args):
                raise ValueError("unsupported synopsis placeholder")
            # Never evaluate documentation as shell code or contact a live target.
            result = subprocess.run(
                [executable, *args, "--help"],
                cwd=root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=30,
            )
            if result.returncode:
                raise ValueError(f"exit {result.returncode}\n{result.stdout}")
            print(f"OK {location}")
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            failed += 1
            print(f"FAIL {location}: roadrunner {text}\n{error}", file=sys.stderr)

if not checked:
    sys.exit("No documented roadrunner commands found.")
print(f"Checked {checked} documented commands; {failed} failed.")
sys.exit(bool(failed))
PY
