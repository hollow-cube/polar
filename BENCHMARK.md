# Benchmark

> Reminder: The benchmark is very simple, and should only be valued as a rough estimate.

The tests were run against [`minestom-ce`](https://github.com/hollow-cube/minestom-ce) on 1.19.4 (`f13a7b49fa`),
on a Macbook Pro (M1 Max). The source code of the test can be seen below.

```java
public class ScuffedBenchmark {
    public static void main(String[] args) throws Exception {
        MinecraftServer.init();
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        long start = System.nanoTime();

        for (int iter = 0; iter < 10; iter++) {
            System.out.println("Starting iteration " + iter);
            // TNTLoader loader = new TNTLoader(new FileTNTSource(Path.of("src/test/resources/bench/bench.tnt")));
            // AnvilLoader loader = new AnvilLoader(Path.of("src/test/resources/bench"));
            PolarLoader loader = new PolarLoader(PolarReader.read(Files.readAllBytes(Path.of("src/test/resources/bench.polar"))));
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    loader.loadChunk(instance, 0, 0).join();
                }
            }

        }

        long end = System.nanoTime();
        System.out.println("Took " + (end - start) / 1_000_000_000.0 / 10.0 + " seconds/iter");
        MinecraftServer.stopCleanly();
    }
}
```
