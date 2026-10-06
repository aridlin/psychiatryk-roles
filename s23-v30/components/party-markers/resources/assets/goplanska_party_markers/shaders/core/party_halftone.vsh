#version 150
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 WorldFromView;
out vec3 worldRelative;
void main(){vec4 view=ModelViewMat*vec4(Position,1);gl_Position=ProjMat*view;worldRelative=(WorldFromView*view).xyz;}
