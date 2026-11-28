#line 2

/*
 * Copyright (c) 2023 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

precision lowp int;
precision highp float;
precision highp sampler2D;

layout(location = 0) out vec4 output4f;

uniform sampler2D u_gbufferColorTexture;
uniform sampler2D u_gbufferNormalTexture;
uniform sampler2D u_gbufferMaterialTexture;

uniform sampler2D u_gbufferDepthTexture;
uniform sampler2D u_metalsLUT;

uniform mat4 u_inverseProjectionMatrix4f;
uniform mat4 u_inverseViewMatrix4f;

uniform vec2 u_viewportSize2f;
uniform vec3 u_lightPosition3f;
uniform vec3 u_lightColor3f;

#define LIB_INCLUDE_PBR_LIGHTING_FUNCTION
#EAGLER INCLUDE (3) "eagler:glsl/deferred/lib/pbr_lighting.glsl"

void main() {
	vec2 v_position2f = gl_FragCoord.xy * u_viewportSize2f;

	float depth = textureLod(u_gbufferDepthTexture, v_position2f, 0.0).r;
	if(depth < 0.00001) {
		discard;
	}

	vec4 viewSpacePosition4f = u_inverseProjectionMatrix4f * (vec4(v_position2f, depth, 1.0) * 2.0 - 1.0);
	vec4 worldSpacePosition4f = u_inverseViewMatrix4f * viewSpacePosition4f;
	// one dot serves the falloff and the normalise() of the light vector below
	vec3 lightDist3f = (worldSpacePosition4f.xyz / worldSpacePosition4f.w) - u_lightPosition3f;
	float dist2 = dot(lightDist3f, lightDist3f);

	vec3 color3f = u_lightColor3f / dist2;

	if(color3f.r + color3f.g + color3f.b < 0.025) {
		discard;
	}

	vec4 sampleVar4f = textureLod(u_gbufferNormalTexture, v_position2f, 0.0);
	vec3 worldSpaceNormal = normalize(mat3(u_inverseViewMatrix4f) * (sampleVar4f.rgb * 2.0 - 1.0));
	vec3 materialData3f = textureLod(u_gbufferMaterialTexture, v_position2f, 0.0).rgb;

	// Facing test on the unnormalised light vector: normalize() only scales by the positive
	// 1/|d|, so it cannot flip the sign of dot(lightDir, N). Testing before the normalise lets
	// every fragment this light cannot reach skip it, the colour divide and the whole BRDF.
	if(materialData3f.b != 1.0 && dot(lightDist3f, worldSpaceNormal) >= 0.0) {
		discard;
	}

	vec3 lightDir3f = materialData3f.b == 1.0 ? worldSpaceNormal : lightDist3f * (-inversesqrt(dist2));
	vec3 viewDir3f = -normalize(mat3(u_inverseViewMatrix4f) * (viewSpacePosition4f.xyz / viewSpacePosition4f.w));

	sampleVar4f = textureLod(u_gbufferColorTexture, v_position2f, 0.0);
	vec3 diffuseColor3f = sampleVar4f.rgb * sampleVar4f.rgb;

	output4f = vec4(eaglercraftLighting(diffuseColor3f, color3f, viewDir3f, lightDir3f, worldSpaceNormal, materialData3f), 0.0);
}
