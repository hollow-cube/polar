package net.hollowcube.polar;

public interface ChunkSelector {
    static ChunkSelector all() {
        return (x, z) -> true;
    }

    static ChunkSelector radius(int radius) {
        return radius(0, 0, radius);
    }

    static ChunkSelector radius(int centerX, int centerZ, int radius) {
        return (x, z) -> {
            int dx = x - centerX;
            int dz = z - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        };
    }

    boolean test(int x, int z);

}
