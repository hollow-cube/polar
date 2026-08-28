# polar (fabric fork)

Fork of [hollow-cube/polar](https://github.com/hollow-cube/polar) with the Minestom dependency
removed, so the world format can be used from a Fabric mod.

Polar stores a whole world in a single file. It is meant for small worlds, since the entire file
is read at once and kept in memory.

## What changed from upstream

- No Minestom, no Adventure, no fastutil, no zstd-jni. Plain Java, zero dependencies.
- `NetworkBuffer` replaced with `PolarBuffer`.
- Block entity NBT is kept as raw network-NBT bytes instead of `CompoundBinaryTag`, so whoever
  loads the world decodes it with their own NBT library.
- Added a `DEFLATE` compression type. `ZSTD` files can still be recognised but not read or
  written by this build.
- Dropped the Minestom-only pieces: `PolarLoader`, `StreamingPolarLoader`, `PolarWorldAccess`,
  `UnsafeOps`, `AnvilPolar`.

## Usage

```java
PolarWorld world = PolarReader.read(Files.readAllBytes(path));
byte[] bytes = PolarWriter.write(world);
```

The format itself is unchanged and is described in [FORMAT.md](FORMAT.md).

## License

MIT, same as upstream.
