#version 150
uniform vec4 ColorModulator;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform vec3 PeebTarget;
uniform vec4 PeebLocalFade;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec4 vertexColor;
in vec4 overlayColor;
in float vertexDistance;
in vec3 peebViewPosition;
out vec4 fragColor;
const int bayer[16] = int[16](0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5);
void main() {
    vec4 color = vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    // Character-only low-colour dithering; the rest of Minecraft keeps its chosen shader style.
    ivec2 pixel = ivec2(floor(gl_FragCoord.xy / 2.0));
    float threshold = (float(bayer[(pixel.y & 3) * 4 + (pixel.x & 3)]) + 0.5) / 16.0;
    // Only the local character opens ordered depth holes around a target that
    // lies behind it. The ring itself still uses the world's ordinary depth test.
    if (PeebLocalFade.x > 0.5) {
        float opacity = mix(0.12, 1.0, smoothstep(0.4, 1.25, length(peebViewPosition)));
        if (PeebLocalFade.y > 0.5) {
            vec4 targetClip = ProjMat * ModelViewMat * vec4(PeebTarget, 1.0);
            if (targetClip.w > 0.0001) {
                vec3 targetNdc = targetClip.xyz / targetClip.w;
                vec2 centre = (targetNdc.xy * 0.5 + 0.5) * ScreenSize;
                float radius = clamp(abs(ProjMat[1][1]) * PeebLocalFade.z / targetClip.w * ScreenSize.y * 0.5, 38.0, 180.0);
                float overlap = 1.0 - smoothstep(radius * 0.72, radius, length(gl_FragCoord.xy - centre));
                float inFront = step(gl_FragCoord.z, targetNdc.z * 0.5 + 0.5 + 0.00005);
                opacity = min(opacity, mix(1.0, PeebLocalFade.w, overlap * inFront));
            }
        }
        // Screen-locked Bayer coverage has no frame counter or random noise.
        if (threshold > opacity) discard;
    }
    color.rgb = floor(clamp(color.rgb,0.0,1.0) * 64.0 + threshold) / 64.0;
    float fog = smoothstep(FogStart, max(FogStart + 0.001,FogEnd), vertexDistance);
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fog * FogColor.a), color.a);
}
