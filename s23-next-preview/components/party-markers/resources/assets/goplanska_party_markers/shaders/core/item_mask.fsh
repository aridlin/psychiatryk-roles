#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec3 worldRelative;
out vec4 fragColor;
void main(){if(texture(Sampler0,texCoord).a<.1)discard;fragColor=vec4(worldRelative,1);}
