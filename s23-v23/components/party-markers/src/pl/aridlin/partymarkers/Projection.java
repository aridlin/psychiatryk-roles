package pl.aridlin.partymarkers;

final class Projection {
    private Projection() {}

    // Deliberately ignore clip Z: a teammate beyond the terrain far plane is still marked.
    static float[] screen(float x, float y, float w) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || w <= 0.001f) return null;
        float nx = x / w, ny = y / w;
        if (Math.abs(nx) > 1 || Math.abs(ny) > 1) return null;
        return new float[] {(nx + 1) * 0.5f, (1 - ny) * 0.5f};
    }
}
