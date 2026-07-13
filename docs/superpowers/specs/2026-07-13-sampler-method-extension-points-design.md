# Sampler Method Extension Points

Date: 2026-07-13
Status: Approved (design); implementation plan to follow
Issue: [#101](https://github.com/symentispl/roadrunner/issues/101)

## Context

`Sampler` (`roadrunner-api`) is a single-method functional interface:

```java
public interface Sampler extends AutoCloseable {
    SamplerResponse<?> execute(SamplerParameters parameters);
    default void close() {}
}
```

Every sampler today — JDBC, Neo4j, AB (HTTP), VM — models its one operation
(a fixed query, a fixed HTTP request, a sleep) as this single `execute` entry
point, built once by `SamplerProvider.newSampler()`. `SamplerParameters` is an
ordered bag whose interpretation each sampler invents for itself (JDBC binds
values positionally to `?` placeholders; Neo4j passes the whole map by name;
AB currently ignores it — the request is fixed at construction).

This works because no existing sampler needs more than one operation. It
breaks down for protocols with a natural set of distinct operations (an HTTP
sampler's `GET`/`POST`/`PUT`, a JDBC sampler that might want `query` versus
`update`) — today those would have to be jammed into flags (`-m GET`) or
separate CLI subcommands, with no type-checked parameter list of their own.

Issue #101 proposes letting a sampler expose its own named methods (e.g.
`JDBCSampler.query(String sql)`) and have a small CLI expression
(`query("SELECT * FROM table")`) parsed and bound, via `MethodHandle`s, to the
matching method — producing an ordinary `Sampler` that plugs into the
existing engine unchanged.

`Sampler.execute()` is on the hot path (called every iteration by
`OpenWorldStrategy`/`ClosedWorldStrategy`), which is why the issue calls for
`MethodHandle`s rather than reflection (`Method.invoke`) for the one-time
bind.

## Goals

1. Let a sampler class expose multiple named operations instead of one fixed
   `execute()`, each with its own typed parameter list.
2. A small CLI expression syntax — `methodName("literal", "literal", ...)` —
   selects the operation and supplies its literal arguments, parsed and bound
   once per sampler setup (not per request).
3. The binder produces a standard `Sampler` instance; nothing downstream
   (`SamplerProvider`, `SamplerPlugin`, `ExecutionStrategy`, picocli wiring,
   JPMS `ServiceLoader` discovery) changes.
4. Both mechanisms coexist permanently: a sampler can keep implementing
   `Sampler`/`SamplerProvider` directly for simple, single-operation cases
   (AB, VM stay this way), or expose named methods for richer cases (JDBC,
   Neo4j migrate to this as the worked examples).
5. Per-request parameter binding (`SamplerParameters` → `?` placeholders,
   named map, etc.) is unchanged — it still happens inside the `Sampler`
   returned by an extension-point method, exactly as it does today inside
   `SamplerProvider.newSampler()`'s hand-written lambda.

## Non-Goals

- No per-request/dynamic values in the CLI expression. Arguments are literal
  strings, resolved once at sampler setup — never sourced from a
  `ParameterSource` row. (Confirmed with the issue author: the framework-bound
  value is `SamplerParameters`, and that binding continues to happen exactly
  where it happens today — inside the returned `Sampler`'s `execute` body —
  not as an extension-point method parameter.)
- No migration of AB or VM samplers. They keep implementing `Sampler`
  directly, demonstrating that the simple path still works.
- No literal argument types beyond `String` in this change. Neither JDBC nor
  Neo4j need numeric/`URL`/file-content (`@File`) literals; the converter
  point is a single, obvious extension seam for later.
- No new Maven module and no new external dependency (parser generator,
  reflection library). `MethodHandle`/`MethodHandleProxies` are JDK stdlib;
  the hand-rolled parser follows the existing precedent of `PrefixedMap` in
  `roadrunner-cli`.
- No change to `SamplerParameters`, `ParameterSource`, `ParameterFeed`, or any
  `ExecutionStrategy`.

## Architecture

```
roadrunner-samplers-spi
  ├─ SamplerExpression   (new)  — parses "name(\"lit\", ...)" into an IR
  └─ SamplerExtensionPoint (new) — validates + MethodHandle-binds + adapts

           used by (construction time only)
                    │
                    ▼
roadrunner-sampler-jdbc / roadrunner-sampler-neo4j
  ├─ JDBCSampler / Neo4jSampler   (new "methods" classes)
  └─ JDBCSamplerProvider / Neo4jSamplerProvider  (unchanged shape: still
     implement SamplerProvider.newSampler(), now delegating to the bound
     factory instead of hand-building the Sampler lambda inline)

roadrunner-sampler-ab / roadrunner-sampler-vm — untouched, still implement
Sampler/SamplerProvider directly.

roadrunner-cli, roadrunner-samplers-spi's SamplerPlugin/SamplerOptions/JPMS
ServiceLoader discovery, roadrunner-core's ExecutionStrategy — all unchanged.
```

No new module. Both new types live in `roadrunner-samplers-spi`, alongside
`SamplerOptions`/`SamplerPlugin`, since every sampler module already depends
on it.

## `SamplerExpression`

```java
package io.roadrunner.samplers.spi;

public record SamplerExpression(String methodName, List<String> arguments) {
    public static SamplerExpression parse(String input) { ... }
}
```

Grammar (intentionally minimal — only what JDBC/Neo4j need):

```
expression := name '(' ( stringLiteral (',' stringLiteral)* )? ')'
name       := letter (letter | digit)*
stringLiteral := '"' (escaped-char | non-quote-char)* '"'
```

Implementation follows `PrefixedMap.parse`'s hand-rolled character-state-machine
style (no parser-generator dependency). Malformed input throws
`IllegalArgumentException` with a message naming the offending position —
surfaced at CLI startup, before any sample is taken.

## `SamplerExtensionPoint`

```java
package io.roadrunner.samplers.spi;

public final class SamplerExtensionPoint {
    public static Supplier<Sampler> bind(Object target, SamplerExpression expression) { ... }
}
```

Binding steps:

1. **Validate eligibility.** Every public method declared on `target`'s class
   (excluding anything inherited from `Object`/`AutoCloseable`) must:
   - return exactly `Sampler`,
   - take only `String` parameters (the only literal type supported today).

   Validation runs over *all* public methods up front, not just the one
   matching the expression's method name — a badly-shaped method on a
   sampler class fails at plugin construction time regardless of which
   operation the CLI expression picked. Violations throw
   `PluginInitializationException` naming the offending method and why.

2. **Resolve.** Find the method named `expression.methodName()` whose
   parameter count equals `expression.arguments().size()`. No match (wrong
   name or wrong arity) throws `IllegalArgumentException` listing the
   available methods and their arities.

3. **Bind.** `MethodHandles.lookup().unreflect(method)` → `MethodHandle` with
   receiver as the leading parameter; `MethodHandles.insertArguments` binds
   `target` plus every literal argument, fully saturating the handle (zero
   parameters remain).

4. **Wrap.** Return `() -> { try { return (Sampler) handle.invoke(); } catch (Throwable t) { throw ...; } }`.
   Each invocation of the returned `Supplier<Sampler>` calls the fully-bound
   handle again, producing a fresh `Sampler` — this mirrors what
   `SamplerProvider.newSampler()` already does today for JDBC/Neo4j/AB (build
   a fresh instance per call) and requires no thread-safety assumptions from
   the sampler author about a shared instance.

Binding (steps 1–3) happens once, when the `SamplerProvider` is constructed.
Step 4's `Supplier` is invoked once per `SamplerProvider.newSampler()` call
(once per virtual thread), same call frequency as today.

## Migration: JDBC and Neo4j

**JDBC.** New `JDBCSampler` class:

```java
public class JDBCSampler {
    private final DataSource dataSource;
    public JDBCSampler(DataSource dataSource) { this.dataSource = dataSource; }

    public Sampler query(String sql) {
        return (SamplerParameters parameters) -> {
            // identical body to today's JDBCSamplerProvider.newSampler() lambda:
            // acquire connection, prepareStatement(sql), bind parameters
            // positionally to '?' placeholders, execute, record timings.
        };
    }
}
```

`JDBCSamplerProvider.newSampler()` becomes:

```java
@Override
public Sampler newSampler() {
    return samplerSupplier.get(); // samplerSupplier = SamplerExtensionPoint.bind(jdbcSampler, expression)
}
```

`JDBCSamplerOptions` keeps `--url/--username/--password/--driver/--driver-class
/--pool-size` exactly as-is (infra config is orthogonal to the issue). Only
the existing `@Parameters public String query` positional argument changes
shape: instead of a bare SQL string, it captures a full expression, e.g.

```
jdbc --url jdbc:postgresql://localhost/db --username u --password p \
     --driver driver.jar 'query("SELECT * FROM table WHERE id = ?")'
```

`JDBCSamplerPlugin.newSamplerProvider` parses that string with
`SamplerExpression.parse`, builds the `JDBCSampler` with the constructed
`DataSource`, and calls `SamplerExtensionPoint.bind(jdbcSampler, expression)`
once to get the `Supplier<Sampler>` handed to `JDBCSamplerProvider`.

**Neo4j.** Same shape: `Neo4jSampler.query(String cypher)` returns a `Sampler`
whose body is today's `Neo4jSamplerProvider.newSampler()` lambda (run
`cypher` against a session, passing `parameters.asMap()`), wired the same way
through `Neo4jSamplerPlugin`.

Runtime behavior for both is unchanged — same connection/session handling,
same per-request parameter binding, same metrics. Only the wiring from CLI
string to bound method changes.

## AB and VM — unchanged

Both keep implementing `Sampler`/`SamplerProvider` directly, proving the
simple, single-operation path remains fully supported. No code in either
module changes as part of this issue.

## Error handling

All new failure modes are structural and caught before any sample is taken:

- Parse errors (`SamplerExpression.parse`) → `IllegalArgumentException` at
  CLI argument parsing (surfaces the same way picocli option errors do
  today).
- Validation errors (ineligible method signature) → `PluginInitializationException`
  at `SamplerPlugin.newSamplerProvider(...)` time — same exception type
  `JDBCSamplerPlugin` already throws for driver-loading failures.
- Resolution errors (no matching method / arity mismatch) →
  `IllegalArgumentException` listing the sampler's available methods.

No new exception type reaches `ExecutionStrategy` or the hot loop; the
`Supplier<Sampler>` returned by `bind()` only re-throws if the *target
method's own body* throws when invoked (e.g. a constructor-like failure),
which is no different from today's `newSampler()` contract.

## Testing

- New `roadrunner-samplers-spi` test source set (none exists yet — add a
  junit dependency to its `pom.xml`, matching sibling sampler modules):
  - `SamplerExpressionTest`: valid parses (zero/one/multiple string args),
    malformed input (unterminated literal, missing parens, empty method
    name).
  - `SamplerExtensionPointTest`: successful bind + invoke round-trip against
    a small in-test fixture class; rejection of a method with a non-`String`
    parameter; rejection of a method returning something other than
    `Sampler`; arity-mismatch and unknown-method-name errors.
- Existing `JDBCSamplerProviderIT` (`roadrunner-sampler-jdbc/src/it`) and
  `Neo4jSamplerPluginIT` (`roadrunner-sampler-neo4j/src/it`) continue to
  exercise the migrated samplers end-to-end (real Postgres/Neo4j) — updated
  only to pass the new `query("...")` expression syntax instead of a bare SQL
  string, asserting identical behavior to before the migration.

## Risks

1. **`MethodHandleProxies`/`unreflect` under JPMS.** Each sampler module is a
   named module with explicit `exports`; reflective access via
   `MethodHandles.lookup()` from within `roadrunner-samplers-spi` calling
   into `JDBCSampler` (a different module) requires that module to `open` or
   `exports` the package to `roadrunner-samplers-spi`, similar to the
   existing `opens ... to info.picocli` directives already present in each
   sampler module's `module-info.java`. Verified case-by-case during
   implementation.
2. **Shared vs. fresh `Sampler` per thread.** Steps 3–4 re-invoke the fully
   bound handle on every `Supplier.get()` call, so a naive `query(String
   sql)` implementation that allocates per call (as JDBC/Neo4j already do)
   behaves identically to today. A sampler author who instead wants one
   shared, stateless `Sampler` can simply have their method return the same
   cached instance — the mechanism doesn't force either choice.
3. **Validation false positives.** Scanning *all* public methods (not just
   the invoked one) means a sampler class must keep every public method
   extension-point-eligible, or explicitly keep helper methods
   package-private/private. This is documented behavior, not a defect, but
   worth calling out clearly in Javadoc on the methods class so authors don't
   trip over it.

## Open questions (deferred to the implementation plan)

- Exact wording/format of validation and resolution error messages (how much
  detail to include about available methods/arities).
- Whether `SamplerExpression`'s grammar should reject or silently accept
  extra whitespace around literals/commas — lean toward accepting it, to be
  decided during implementation.
- Whether a shared helper for "build a `DataSource`-backed methods class + a
  `SamplerProvider` that delegates to `SamplerExtensionPoint`" is worth
  factoring out once a third sampler adopts this pattern. Not needed for two
  samplers — revisit if a third migration arrives.
