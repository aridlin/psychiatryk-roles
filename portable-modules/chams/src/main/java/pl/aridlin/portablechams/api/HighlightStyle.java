package pl.aridlin.portablechams.api;

/** RGB colour and optional occluded fill. Outlines remain visible when halftone is false. */
public record HighlightStyle(int rgb, boolean halftone) {
    public HighlightStyle { rgb &= 0xFFFFFF; }
    public static HighlightStyle outline(int rgb) { return new HighlightStyle(rgb, false); }
    public static HighlightStyle halftone(int rgb) { return new HighlightStyle(rgb, true); }
}
