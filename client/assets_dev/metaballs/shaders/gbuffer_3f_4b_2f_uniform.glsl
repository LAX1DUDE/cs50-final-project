
#ifdef VERT

layout(location = 0) in vec3 posIn;
layout(location = 1) in vec4 normIn;
layout(location = 2) in vec2 texIn;

out vec2 v_texCoord;
out vec3 v_normal;

uniform mat4 matrix_mvp;
uniform mat4 matrix_m_invtrans;

void main() {
	v_texCoord = texIn;
	v_normal = (mat3(matrix_m_invtrans) * normIn.xyz).xyz;
    gl_Position = matrix_mvp * vec4(posIn, 1.0);
}

#endif

#ifdef FRAG

in vec2 v_texCoord;
in vec3 v_normal;

layout(location = 0) out vec4 diffuse;  // diffuseRGB, ditherBlend
layout(location = 1) out vec4 material; // metallic, roughness, specular, ssr
layout(location = 2) out vec4 normal;   // normalXYZ, emission

uniform sampler2D tex;

uniform float ditherBlend;
uniform float metallic;
uniform float roughness;
uniform float specular;
uniform float ssr;
uniform float emission;

void main() {
	if(ditherBlend > 0.0 && mod(gl_FragCoord.x + gl_FragCoord.y, 2.0) == 0.0) discard;
    diffuse = vec4(pow(texture(tex, v_texCoord).rgb, vec3(2.2)), ditherBlend);
	material = vec4(metallic, roughness, specular, ssr);
	normal = vec4(normalize(v_normal) * 0.5 + 0.5, emission);
}

#endif
