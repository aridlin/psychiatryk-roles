#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in vec3 Normal;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 ScooterNormalMat;
uniform vec2 ScooterLight;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
void main() {
    vec4 cameraVertex = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * cameraVertex;
    vertexDistance = fog_distance(cameraVertex.xyz, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(ScooterNormalMat * Normal), Color);
    lightMapColor = texelFetch(Sampler2, ivec2(ScooterLight), 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
