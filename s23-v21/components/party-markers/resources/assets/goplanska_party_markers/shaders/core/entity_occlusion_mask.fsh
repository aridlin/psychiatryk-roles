#version 150
uniform sampler2D Sampler0;
uniform float AlphaCutoff;
uniform float EntityKey;
uniform vec4 ColorModulator;
in float vertexAlpha;
in vec2 texCoord;
in vec3 worldRelative;
out vec4 fragColor;
void main(){float alpha=texture(Sampler0,texCoord).a*vertexAlpha*ColorModulator.a;if(alpha<=0||alpha<AlphaCutoff)discard;fragColor=vec4(worldRelative,EntityKey);}
