#ifdef VERT

layout(location = 0) in vec3 posIn;

uniform mat4 matrix_mvp;

void main() {
    gl_Position = matrix_mvp * vec4(posIn, 1.0);
}

#endif

#ifdef FRAG

#define PI 3.14159265

//include dependencies/pbr_common.glsl
#line 19

layout(location = 0) out vec3 diffuseOut;
layout(location = 1) out vec3 specularOut;

uniform sampler2D material; // metallic, roughness, specular, ssr
uniform sampler2D normal;   // normalXYZ, emission
uniform sampler2D position; // position

uniform vec3 lightPosition;
uniform vec3 lightDirection;
uniform vec3 lightColor;
uniform float emission;
uniform float radiusF;
uniform float size;

uniform vec2 screenSize;

uniform mat4 matrix_v_inv;
uniform mat4 matrix_p_inv;

vec3 getPosition(sampler2D dt, vec2 coord) {
	float depth = texture(dt, coord).r;
	vec4 tran = matrix_p_inv * vec4(coord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	return (matrix_v_inv * vec4(tran.xyz / tran.w, 1.0)).xyz;
}

void main() {
	vec4 diffuseV;
	vec4 materialV;
	vec4 normalV;
	vec3 positionV;
	vec3 normalC;
	
	vec2 v_texCoord = gl_FragCoord.xy / screenSize;
	
	normalV = texture(normal, v_texCoord);
	positionV = getPosition(position, v_texCoord);
	normalC = normalize(normalV.xyz * 2.0 - 1.0);
	
	vec3 L = normalize(lightPosition - positionV);
	float NdotL = max(dot(normalC, L), 0.0);
	if(NdotL > 0.0) {
		float spotCutoff = max(dot(-lightDirection, L), 0.0);
		float outercutoff = 1.0 - radiusF;
		float cutoff = outercutoff + (size / 360.0);
		if(spotCutoff > outercutoff) {
			materialV = texture(material, v_texCoord);
			float ep = max(cutoff - outercutoff, 0.01);
		
			vec3 V = normalize(-positionV);
			vec3 H = normalize(V + L);
		
			vec3 F0 = vec3(0.04);
			F0 = mix(F0, vec3(1.0), materialV.r);
		
			float distance = length(lightPosition - positionV);
			float attenuation = 1.0 / (distance * distance);
			vec3 radiance = lightColor * max(attenuation * emission - 0.2, 0.0) * clamp((spotCutoff - outercutoff) / ep, 0.0, 1.0);
			
			float roughness = materialV.g;
			float NDF = DistributionGGX(normalC, H, roughness);   
			float G   = GeometrySmith(normalC, V, L, roughness);      
			vec3  F   = fresnelSchlick(clamp(dot(H, V), 0.0, 1.0), F0);
			
			vec3 kS = F;
			vec3 kD = vec3(1.0) - kS;
			kD *= 1.0 - materialV.r;
		
			vec3 nominator    = NDF * G * F; 
			float denominator = 0.25 * max(dot(normalC, V), 0.0) * max(dot(normalC, L), 0.0);
			vec3 specular = nominator / max(denominator, 0.001);
		
			diffuseOut = radiance * (kD / PI * NdotL);// + vec3(0.05);
			specularOut = radiance * specular * NdotL * materialV.b;
		}else {
			diffuseOut = vec3(0.0);
			specularOut = vec3(0.0);
		}
	}else {
		diffuseOut = vec3(0.0);
		specularOut = vec3(0.0);
	}
}

#endif
