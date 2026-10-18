#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Vanilla 1.21.11 rendertype_text, plus the SÜLD corner minimap (docs/WORLD_MAP.md): a 128 x 128 map whose first
// row starts with the four marker colours of mn.suld.api.map.Minimap#MARK is drawn in the top-left corner of the
// screen, at a whole number of screen pixels per map pixel, instead of where its item frame hangs. Everything else
// is drawn exactly as vanilla draws it.
//
// The map is found in its own 128 x 128 texture (vanilla) or in a 128-cell map atlas (ImmediatelyFast's
// map_atlas_generation, on by default in Fabulously Optimized: it copies every map into one 2048 x 2048 texture, and
// a minimap looked for in 128 x 128 textures only hung in the world as four floating maps).

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
out vec2 suldLocal;
flat out int suldMinimap;

bool suld_mark(ivec2 at, vec3 rgb) {
    vec3 c = texelFetch(Sampler0, at, 0).rgb * 255.0;
    return all(lessThan(abs(c - rgb), vec3(1.5)));
}

bool suld_marked(ivec2 o) {
    return suld_mark(o, vec3(178.0, 76.0, 216.0)) && suld_mark(o + ivec2(1, 0), vec3(48.0, 115.0, 112.0))
        && suld_mark(o + ivec2(2, 0), vec3(176.0, 168.0, 54.0)) && suld_mark(o + ivec2(3, 0), vec3(81.0, 27.0, 27.0));
}

// This vertex's corner of the minimap, (0, 0) top-left to (1, 1) bottom-right, or (-1, -1) when it is not one.
vec2 suld_minimap_corner() {
    ivec2 size = textureSize(Sampler0, 0);
    if (size == ivec2(128, 128)) return suld_marked(ivec2(0, 0)) ? UV0 : vec2(-1.0);
    // a map atlas: square, 128-texel cells, every corner of a map on a cell boundary (glyph corners almost never are).
    // Never a 256 font page (1.21.11's glyph atlas): a glyph at a page's (0, 0) whose first texels matched the markers
    // would have one vertex pulled into the corner (check_minimap_shader.py); map atlases are 2048 texels and more.
    if (size.x != size.y || size.x < 512 || size.x % 128 != 0) return vec2(-1.0);
    ivec2 at = ivec2(round(UV0 * vec2(size)));
    if (any(notEqual(at % 128, ivec2(0))) || any(greaterThan(abs(UV0 * vec2(size) - vec2(at)), vec2(0.01)))) return vec2(-1.0);
    // a boundary touches up to four cells, so the corner comes from the quad's vertex order, the order vanilla and
    // ImmediatelyFast both draw a map in: bottom-left, bottom-right, top-right, top-left
    int v = gl_VertexID % 4;
    ivec2 corner = ivec2(v == 1 || v == 2 ? 1 : 0, v < 2 ? 1 : 0);
    ivec2 o = at - corner * 128;
    if (any(lessThan(o, ivec2(0))) || any(greaterThan(o + 128, size))) return vec2(-1.0);
    return suld_marked(o) ? vec2(corner) : vec2(-1.0);
}

void main() {
    vec2 corner = suld_minimap_corner();
    if (corner.x >= 0.0) {
        // whole screen pixels per map pixel: 2 at 1080p, 3 at 1440p, 4 at 2160p, 1 below 900 px
        float scale = max(1.0, floor(ScreenSize.y / 450.0));
        float margin = 3.0 * scale;
        // corner (0, 0) is the picture's top-left; row 0 (the marker row) sits above the margin and is discarded
        vec2 px = vec2(margin, margin - scale) + corner * (128.0 * scale);
        gl_Position = vec4(px.x / ScreenSize.x * 2.0 - 1.0, 1.0 - px.y / ScreenSize.y * 2.0, -0.999, 1.0);
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        vertexColor = vec4(1.0);
        texCoord0 = UV0;
        suldLocal = corner;
        suldMinimap = 1;
        return;
    }
    suldMinimap = 0;
    suldLocal = vec2(0.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
