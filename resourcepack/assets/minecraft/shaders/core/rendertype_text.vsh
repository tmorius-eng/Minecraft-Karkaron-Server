#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Vanilla 1.21.11 rendertype_text, plus the SÜLD corner minimap (docs/WORLD_MAP.md): a 128 x 128 map whose first
// row starts with the four marker colours of mn.suld.api.map.Minimap#MARK is drawn in the top-left corner of the
// screen, at a whole number of screen pixels per map pixel, instead of where its item frame hangs. Everything else
// is drawn exactly as vanilla draws it.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int suldMinimap;

bool suld_mark(int x, vec3 rgb) {
    vec3 c = texelFetch(Sampler0, ivec2(x, 0), 0).rgb * 255.0;
    return all(lessThan(abs(c - rgb), vec3(1.5)));
}

bool suld_is_minimap() {
    if (textureSize(Sampler0, 0) != ivec2(128, 128)) return false;
    return suld_mark(0, vec3(178.0, 76.0, 216.0)) && suld_mark(1, vec3(48.0, 115.0, 112.0))
        && suld_mark(2, vec3(176.0, 168.0, 54.0)) && suld_mark(3, vec3(81.0, 27.0, 27.0));
}

void main() {
    if (suld_is_minimap()) {
        // whole screen pixels per map pixel: 2 at 1080p, 3 at 1440p, 4 at 2160p, 1 below 900 px
        float scale = max(1.0, floor(ScreenSize.y / 450.0));
        float margin = 3.0 * scale;
        // UV (0, 0) is the picture's top-left; row 0 (the marker row) sits above the margin and is discarded
        vec2 px = vec2(margin, margin - scale) + UV0 * (128.0 * scale);
        gl_Position = vec4(px.x / ScreenSize.x * 2.0 - 1.0, 1.0 - px.y / ScreenSize.y * 2.0, -0.999, 1.0);
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        vertexColor = vec4(1.0);
        texCoord0 = UV0;
        suldMinimap = 1;
        return;
    }
    suldMinimap = 0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
