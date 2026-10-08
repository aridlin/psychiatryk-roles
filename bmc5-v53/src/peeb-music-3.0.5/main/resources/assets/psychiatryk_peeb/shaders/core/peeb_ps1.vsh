#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
out vec4 vertexColor;
out vec4 overlayColor;
out float vertexDistance;
out vec3 peebViewPosition;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    peebViewPosition = view.xyz;
    gl_Position = ProjMat * view;
    // Original Shader49: clip-space snapping; z/w remain intact.
    if (abs(gl_Position.w) > 0.0001) {
        vec2 scale = max(ScreenSize, vec2(1.0)) * 0.75;
        vec2 ndc = gl_Position.xy / gl_Position.w;
        gl_Position.xy = gl_Position.w * roundEven(ndc * scale / 0.125) * 0.125 / scale;
    }
    vec3 light = texelFetch(Sampler2, clamp(UV2 / 16, ivec2(0), ivec2(15)), 0).rgb;
    vec3 n = normalize(Normal);
    float lambert = max(dot(n, normalize(Light0_Direction)), 0.0);
    float secondary = max(dot(n, normalize(Light1_Direction)), 0.0);
    // Native Minecraft light supplies the scene-dependent ambient/directional equivalent.
    vec3 ambient = light * 0.55;
    vec3 continuous = light * (lambert * 0.55 + secondary * 0.25) + ambient;
    continuous -= 0.3 * max(vec3(1.0) - light, vec3(0.0));
    float emission = UV0.x < 0.25 ? 0.05 : UV0.x < 0.75 ? 0.4 : 0.0;
    vertexColor = vec4(Color.rgb * max(continuous + emission, vec3(0.04)), Color.a);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    vertexDistance = length(view.xyz);
}
