package mn.suld.plugin.branding;

import mn.suld.plugin.content.Content;
import mn.suld.plugin.content.ContentPack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DOC-001: in-game counts come from the content pack, never from a literal in the source. */
class ContentCountsTest {

    private static final Pattern LITERAL_COUNT = Pattern.compile("\"[^\"]*\\b\\d+ (бүлэг|агуй|газар|нутаг|дурсгал|овоо)\\b[^\"]*\"");

    @Test
    void placeholdersAreFilledFromTheLoadedContent() {
        ContentPack p = Content.pack();
        assertEquals(p.chapters().size() + " бүлэг, " + p.dungeons().size() + " агуй",
                ContentCounts.fill("{chapters} бүлэг, {dungeons} агуй"));
        assertTrue(p.chapters().size() >= 42 && p.dungeons().size() >= 10, "the counts the old texts got wrong");
        assertEquals("no placeholders", ContentCounts.fill("no placeholders"));
    }

    @Test
    void noPlayerFacingSourceTypesACount() throws IOException {
        Path root = Path.of("src/main/java/mn/suld/plugin");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(root)) {
            offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> {
                        try {
                            return Files.readAllLines(f).stream().filter(l -> LITERAL_COUNT.matcher(l).find())
                                    .map(l -> root.relativize(f) + ": " + l.strip());
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList();
        }
        assertEquals(List.of(), offenders, "use ContentCounts placeholders ({chapters}, {dungeons}, {areas}, …)");
    }
}
