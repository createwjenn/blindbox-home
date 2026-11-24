precision highp float;
varying vec2 uv;
uniform float time,phase,overcast,mode;
const float PI=3.14159265359;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 p){float n=0.,a=.5;for(int i=0;i<5;i++){n+=a*noise(p);p=mat2(1.6,-1.2,1.2,1.6)*p+3.7;a*=.5;}return n;}
vec3 sky(vec3 rd,bool clouds){
 float up=max(rd.y,0.);float horizon=pow(1.-up,3.5);
 vec3 zenith=vec3(.10,.34,.62),edge=vec3(.72,.82,.88),sunColor=vec3(1.,.89,.68);
 vec3 sunDir=normalize(vec3(-.65,.5,.4));
 if(phase<.5){zenith=vec3(.13,.23,.43);edge=vec3(.96,.64,.40);sunDir=normalize(vec3(-.7,.09,.55));}
 if(phase>1.5&&phase<2.5){zenith=vec3(.065,.12,.28);edge=vec3(1.,.40,.12);sunDir=normalize(vec3(-.8,.06,-.4));sunColor=vec3(1.,.58,.24);}
 if(phase>2.5){zenith=vec3(.008,.018,.045);edge=vec3(.12,.17,.25);sunDir=normalize(vec3(.4,.5,.3));sunColor=vec3(.42,.55,.7);}
 vec3 col=mix(zenith,edge,horizon);
 float sd=max(dot(rd,sunDir),0.);
 col+=sunColor*(pow(sd,14.)*.17+pow(sd,260.)*.27+pow(sd,2400.)*.48)*(1.-overcast*.85);
 if(clouds&&rd.y>.015){
   // Project coherent clouds onto a high layer; horizontal drift is very slow.
   vec2 p=rd.xz/(rd.y+.16)*1.7+vec2(time*.004,time*.0015);
   float n=fbm(p);float coverage=mix(.58,.39,overcast);
   float cloud=smoothstep(coverage,coverage+.19,n)*smoothstep(.015,.15,rd.y);
   float shadow=fbm(p+vec2(.35,.12));
   vec3 cc=mix(vec3(.45,.52,.61),vec3(.98,.98,.94),smoothstep(.35,.72,shadow));
   if(phase<.5)cc*=vec3(1.,.79,.68);
   if(phase>1.5&&phase<2.5)cc*=vec3(1.,.58,.40);
   if(phase>2.5)cc*=vec3(.13,.17,.24);
   col=mix(col,cc,cloud*.87);
 }
 if(phase>2.5&&rd.y>.12){vec2 star=vec2(atan(rd.z,rd.x),asin(rd.y))*900.;float h=hash(floor(star));float point=pow(max(0.,1.-length(fract(star)-.5)*2.),8.);col+=vec3(.35,.4,.5)*point*step(.998,h)*(1.-overcast);}
 col=mix(col,vec3(dot(col,vec3(.299,.587,.114))),overcast*.40);
 return col;
}
void main(){
 vec3 col;
 if(mode<.5){
   // Latitude/longitude mesh: vertical texture coordinate runs south to north.
   float lon=uv.x*2.*PI,lat=(uv.y-.5)*PI;
   vec3 rd=vec3(cos(lat)*cos(lon),sin(lat),cos(lat)*sin(lon));
   col=sky(rd,true);
 }else{
   vec2 pos=(uv-.5)*210.;
   float a=pos.x*.65+pos.y*1.8-time*.65;
   float b=pos.x*1.5-pos.y*.85+time*.48;
   float c=pos.x*3.1+pos.y*2.6-time*1.15;
   vec2 slope=vec2(.022*cos(a)*.65+.013*cos(b)*1.5+.006*cos(c)*3.1,.022*cos(a)*1.8-.013*cos(b)*.85+.006*cos(c)*2.6);
   slope+=(vec2(noise(pos*2.+time*.15),noise(pos*2.8-time*.12))-.5)*.045;
   vec3 normal=normalize(vec3(-slope.x,1.,-slope.y));
   vec3 view=normalize(vec3(-pos.x,13.6,-pos.y));
   vec3 refl=reflect(-view,normal);
   float fresnel=.04+.96*pow(1.-max(dot(normal,view),0.),5.);
   vec3 sea=phase>2.5?vec3(.012,.037,.055):vec3(.025,.16,.20);
   col=mix(sea,sky(refl,false),.28+fresnel*.68);
   // Broken waterfront reflections, modulated by the actual moving wave normals.
   float stripes=pow(max(0.,sin(pos.x*1.8+slope.x*34.)),16.);
   float nearShore=exp(-abs(pos.y+19.)*.022);
   if(phase>2.5)col+=vec3(.40,.25,.10)*stripes*nearShore*.25;
   col+=vec3(.016,.028,.033)*sin(a+b)*.5;
 }
 gl_FragColor=vec4(clamp(col,0.,1.),1.);
}
