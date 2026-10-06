#version 150
uniform sampler2D PositionMask;
uniform sampler2D CoverBoxes;
uniform sampler2D TextureCover;
uniform sampler2D EntityCover;
uniform float TargetEntityKey;
uniform int BoxCount;
uniform int HalftoneEnabled;
uniform vec3 CoverOffset;
uniform float FallbackCover;
uniform vec4 TeamColor;
uniform vec2 PatternAnchor;
in vec2 uv;
out vec4 fragColor;
float thickness(vec3 target){float distance=length(target);vec3 ray=target/max(distance,0.00001);vec3 safeRay=mix(vec3(0.000001),ray,greaterThan(abs(ray),vec3(0.000001)));float sum=0;for(int i=0;i<2048;i++){if(i>=BoxCount||sum>=16)break;vec3 a=(texelFetch(CoverBoxes,ivec2(0,i),0).xyz+CoverOffset)/safeRay;vec3 b=(texelFetch(CoverBoxes,ivec2(1,i),0).xyz+CoverOffset)/safeRay;vec3 lo=min(a,b),hi=max(a,b);float enter=max(0,max(lo.x,max(lo.y,lo.z)));float exit=min(distance-.02,min(hi.x,min(hi.y,hi.z)));sum+=max(0,exit-enter);}return max(sum,FallbackCover);}
void main(){ivec2 size=textureSize(PositionMask,0);ivec2 pixel=clamp(ivec2(gl_FragCoord.xy),ivec2(0),size-1);vec4 samplePosition=texelFetch(PositionMask,pixel,0);bool border=false;
 if(samplePosition.a<.5){for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++){if(x*x+y*y>4)continue;vec4 neighbour=texelFetch(PositionMask,clamp(pixel+ivec2(x,y),ivec2(0),size-1),0);if(neighbour.a>.5){samplePosition=neighbour;border=true;}}if(!border)discard;}
 if(border){fragColor=vec4(TeamColor.rgb,.95);return;}
 if(HalftoneEnabled==0)discard;vec4 texCover=texelFetch(TextureCover,pixel,0);bool textureOccluded=texCover.a>.5&&length(texCover.xyz)<length(samplePosition.xyz)-.02;vec4 entityCover=texelFetch(EntityCover,pixel,0);textureOccluded=textureOccluded||(entityCover.a>.5&&abs(entityCover.a-TargetEntityKey)>.25&&length(entityCover.xyz)<length(samplePosition.xyz)-.02);float cover=thickness(samplePosition.xyz);if(textureOccluded)cover=max(cover,1.0);if(cover<=.01)discard;float density=.12+.88*(1-exp(-cover/4));
 // Constant screen-pixel spacing; origin follows the interpolated entity projection.
 const float spacing=14.0;vec2 plane=gl_FragCoord.xy-PatternAnchor;vec2 cell=mod(plane,spacing)-vec2(spacing*.5);float radius=spacing*(.055+.40*density);float feather=.7;float alpha=1-smoothstep(radius-feather,radius+feather,length(cell));if(alpha<.01)discard;fragColor=vec4(TeamColor.rgb,alpha*.85);}
