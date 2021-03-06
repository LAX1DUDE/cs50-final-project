#ifdef VERT

layout(location = 0) in vec3 posIn;
layout(location = 1) in vec2 texIn;

out vec2 v_texCoord;

uniform lowp float scale;

void main() {
	v_texCoord = texIn * scale;
    gl_Position = vec4(posIn, 1.0);
}

#endif

#ifdef FRAG

in vec2 v_texCoord;

layout(location = 0) out vec3 fragOut;

uniform sampler2D tex;

uniform vec2 screenSizeInv;
uniform vec2 offset;

uniform lowp float scale;

void main() {
	float weight[5] = float[5] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
	vec3 result = texture(tex, v_texCoord + offset).rgb * weight[0];
	float j = 1.0;
	for(int i = 1; i < 5; ++i) {
		result += texture(tex, clamp(v_texCoord + offset + vec2(screenSizeInv.x * j, screenSizeInv.y * j), offset, vec2(scale * 0.99) + offset)).rgb * weight[i];
		result += texture(tex, clamp(v_texCoord + offset - vec2(screenSizeInv.x * j, screenSizeInv.y * j), offset, vec2(scale * 0.99) + offset)).rgb * weight[i];
		j += 1.0;
	}
	fragOut = result;
}

#endif