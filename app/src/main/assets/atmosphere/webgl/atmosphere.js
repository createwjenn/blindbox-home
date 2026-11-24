/* Browser WebGL preview of the shared GLSL shaders used by the native PICO GPU renderer.
 * Serve this directory over localhost. No external libraries or network dependencies. */
(async () => {
  const options=new URLSearchParams(location.search),mode=options.get('mode')==='water'?1:0;
  const canvas=document.getElementById('scene');
  const gl=canvas.getContext('webgl',{alpha:false,antialias:false,depth:false,stencil:false,preserveDrawingBuffer:false});
  if(!gl){console.error('SKYLINE_WEBGL_FAILED no WebGL context');return;}
  const vertex=await (await fetch('./atmosphere.vert')).text();
  const fragment=await (await fetch('./atmosphere.frag')).text();
  function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
  let program;
  try{program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));}
  catch(e){console.error('SKYLINE_WEBGL_FAILED '+e.message);return;}
  gl.useProgram(program);const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
  const p=gl.getAttribLocation(program,'p');gl.enableVertexAttribArray(p);gl.vertexAttribPointer(p,2,gl.FLOAT,false,0,0);
  const uniforms={};for(const n of ['time','phase','overcast','mode'])uniforms[n]=gl.getUniformLocation(program,n);
  let phase=Number(options.get('phase')||1),overcast=Number(options.get('cloud')||0),stopped=false,active=true,last=-1000,frames=0;
  window.setAtmosphereActive=v=>{active=v;};
  window.setAtmosphere=(p,c)=>{phase=p;overcast=c;};window.stopAtmosphere=()=>{stopped=true;};
  canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();stopped=true;console.error('SKYLINE_WEBGL_FAILED context lost');});
  function frame(ms){if(stopped)return;requestAnimationFrame(frame);if(!active)return;if(ms-last<(mode?40:90))return;last=ms;
    const width=Number(options.get('width')||1536),height=Number(options.get('height')||768);
    if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;gl.viewport(0,0,width,height);}
    gl.uniform1f(uniforms.time,ms*.001);gl.uniform1f(uniforms.phase,phase);gl.uniform1f(uniforms.overcast,overcast);gl.uniform1f(uniforms.mode,mode);gl.drawArrays(gl.TRIANGLES,0,3);gl.flush();frames++;
    if(frames===3)console.log('SKYLINE_WEBGL_READY '+(mode?'water':'sky'));
    if(frames%120===0)console.log('SKYLINE_WEBGL_FRAME '+(mode?'water':'sky')+' '+frames);
  }
  requestAnimationFrame(frame);
})();
