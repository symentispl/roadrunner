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

1. **Validate eligibility.** Scan `target.getClass().getMethods()` and
   consider only the methods whose return type is exactly `Sampler` — those
   are the extension-point candidates. (This is narrower than the original
   draft of this section, which said *every* public method must qualify;
   that broke on `JDBCSampler` itself, which also needs plain accessor
   methods like `sampleCount()` for `JDBCSamplerPlugin`'s summary log.
   Methods that don't return `Sampler` are simply outside the extension
   point's concern and are left alone — no rejection, no annotation needed
   to hide them.) Each candidate method's parameters must all be `String`
   (the only literal type supported today); a candidate with a non-`String`
   parameter throws `PluginInitializationException` naming the offending
   method and why, at plugin construction time.

2. **Resolve.** Find the method named `expression.methodName()` whose
   parameter count equals `expression.arguments().size()`. No match (wrong
   name or wrong arity) throws `IllegalArgumentException` listing the
   available methods and their arities.

3. **Bind.** `MethodHandles.publicLookup().unreflect(method)` → `MethodHandle`
   with receiver as the leading parameter; `MethodHandles.insertArguments`
   binds `target` plus every literal argument, fully saturating the handle
   (zero parameters remain). `publicLookup()`, not `lookup()`: the target
   class (e.g. `JDBCSampler`) lives in a different module than
   `SamplerExtensionPoint`, and `roadrunner-samplers-spi` must not gain a
   `requires` edge back onto every sampler module that uses it — that would
   invert the plugin dependency direction. `publicLookup()` resolves public
   members of unconditionally-exported packages across module boundaries
   without either module needing a `requires` on the other, which is exactly
   the shape here (`query` is `public` on a `public` class in a package each
   sampler module already `exports` unconditionally).

4. **Wrap.** Return `() -> { try { return (Sampler) handle.invoke(); } catch (Throwable t) { throw ...; } }`.
   Each invocation of the returned `Supplier<Sampler>` calls the fully-bound
   handle again, producing a fresh `Sampler` — this mirrors what
   `SamplerProvider.newSampler()` already does today for JDBC/Neo4j/AB (build
   a fresh instance per call) and requires no thread-safety assumptions from
   the sampler author about a shared instance.

Binding (steps 1–3) happens once, when the `SamplerProvider` is constructed.
Step 4's `Supplier` is invoked once per `SamplerProvider.newSampler()` call
(once per virtual thread), same call frequency as today.

Note what this means for the hot path: the `MethodHandle` is only ever
invoked from step 4, i.e. once per virtual thread at startup. The object it
returns (e.g. the lambda inside `JDBCSampler.query(String sql)`) is an
ordinary, hand-written `Sampler` implementation — `execute(SamplerParameters)`
itself is called through plain interface dispatch, exactly as it is today.
No `MethodHandle` sits on the per-request path.

## Microbenchmarks

The open question is therefore narrower than "does MethodHandle dispatch
slow down sampling" (it doesn't touch the per-request path at all) — it's
"does binding+invoking the factory method through a `MethodHandle` cost
meaningfully more than calling it directly, at the frequency `newSampler()`
is actually called (once per virtual thread)?" The issue also explicitly
motivates the `MethodHandle` choice by contrast with reflection, which is
worth measuring rather than asserting.

Add benchmarks to the existing `roadrunner-microbenchmarks` JMH module (no
new module, no new dependency — `jmh-core` is already there) comparing three
ways of turning a resolved target + literal argument into a `Sampler`:

1. **Direct** — baseline: call `fixture.query(sql)` as an ordinary Java method
   call (today's status quo — no indirection at all).
2. **MethodHandle** — the design above: `Lookup.unreflect` + `insertArguments`
   done once in `@Setup`, then `handle.invoke()` per benchmark invocation.
3. **Reflection** — `Class.getMethod` done once in `@Setup`, then
   `method.invoke(fixture, sql)` per benchmark invocation (Approach B from
   this spec, included so its rejection is backed by a number, not just an
   assertion).

```java
@State(Scope.Benchmark)
public class SamplerFactoryDispatchState {
    JDBCSamplerFixture fixture;   // trivial stand-in, no real DataSource needed
    String sql;
    MethodHandle boundHandle;     // fully saturated: unreflect + insertArguments
    Method reflectMethod;

    @Setup(Level.Trial)
    public void setUp() throws Exception { ... }
}

@Benchmark
public Sampler directDispatch(SamplerFactoryDispatchState s) {
    return s.fixture.query(s.sql);
}

@Benchmark
public Sampler methodHandleDispatch(SamplerFactoryDispatchState s) throws Throwable {
    return (Sampler) s.boundHandle.invoke();
}

@Benchmark
public Sampler reflectionDispatch(SamplerFactoryDispatchState s) throws Exception {
    return (Sampler) s.reflectMethod.invoke(s.fixture, s.sql);
}
```

JMH auto-blackholes the returned `Sampler`, so no manual `Blackhole` plumbing
is needed. Runs through the existing `benchmarks/` tracking scripts
(`task benchmark:run`) like the current `RoadrunnerBenchmarks` — no new
tooling. This is a one-time comparison to sanity-check the design choice, not
a regression gate: `newSampler()` runs once per virtual thread, so even a
measurable per-call delta here is orders of magnitude below anything that
would show up in end-to-end throughput.

If the numbers show the `MethodHandle` path is unexpectedly costly relative
to direct dispatch, the fallback is to cache/reuse resolved handles more
aggressively — not to abandon `MethodHandle`s for `Method.invoke`, which the
benchmark is expected to show is worse, not better.

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
- The JMH microbenchmarks described above (direct vs `MethodHandle` vs
  reflection dispatch) — a one-time design sanity check, run manually via
  `task benchmark:run`, not part of the regular unit/integration test suite.

## Risks

1. **Cross-module reflection under JPMS.** Resolved by using
   `MethodHandles.publicLookup()` (see the Bind step above) rather than
   `MethodHandles.lookup()` — no new `requires`/`opens` directives needed in
   any `module-info.java`, since target classes are `public` in packages each
   sampler module already `exports` unconditionally. Confirmed during
   implementation by the extension-point unit tests actually running.
2. **Shared vs. fresh `Sampler` per thread.** Steps 3–4 re-invoke the fully
   bound handle on every `Supplier.get()` call, so a naive `query(String
   sql)` implementation that allocates per call (as JDBC/Neo4j already do)
   behaves identically to today. A sampler author who instead wants one
   shared, stateless `Sampler` can simply have their method return the same
   cached instance — the mechanism doesn't force either choice.
3. **Ambiguous arity across overloads.** If a methods class ever declares two
   `Sampler`-returning methods with the same name and the same parameter
   count (e.g. two overloads both taking two `String`s), resolution in step 2
   can't disambiguate by name + arity alone. Not a problem for JDBC/Neo4j
   (one `Sampler`-returning method each); `PluginInitializationException` at
   bind time if it ever occurs, rather than silently picking one.

## Open questions (deferred to the implementation plan)

- Exact wording/format of validation and resolution error messages (how much
  detail to include about available methods/arities).
- Whether `SamplerExpression`'s grammar should reject or silently accept
  extra whitespace around literals/commas — lean toward accepting it, to be
  decided during implementation.
- Whether a shared helper for "bui`ld a `DataSource`-backed methods class + a
  `SamplerProvider` that delegates to `SamplerExtensionPoint`" is worth
  factoring out once a third sampler adopts this pattern. Not needed for two
  samplers — revisit if a third migration arrives.
