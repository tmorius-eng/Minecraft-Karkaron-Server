#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Vanilla 1.21.11 rendertype_text, plus the SÜLD corner minimap (see rendertype_text.vsh): drawn with the map's own
// colours (no light, no fog), its marker row hidden.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in int suldMinimap;

out vec4 fragColor;

void main() {
    if (suldMinimap == 1) {
        if (texCoord0.y < 1.0 / 128.0) {
            discard;
        }
        vec4 map = texture(Sampler0, texCoord0);
        if (map.a < 0.1) {
            discard;
        }
        fragColor = vec4(map.rgb, 1.0);
        return;
    }
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
