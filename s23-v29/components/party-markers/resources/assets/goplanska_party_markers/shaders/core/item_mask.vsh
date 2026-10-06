#version 150
in vec3 Position;
in vec2 UV0;
out vec2 texCoord;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 WorldFromView;
out vec3 worldRelative;
void main(){texCoord=UV0;vec4 view=ModelViewMat*vec4(Position,1);gl_Position=ProjMat*view;worldRelative=(WorldFromView*view).xyz;}
