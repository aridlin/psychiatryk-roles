#version 150
in vec3 worldRelative;
out vec4 fragColor;
void main(){fragColor=vec4(worldRelative,1);}
