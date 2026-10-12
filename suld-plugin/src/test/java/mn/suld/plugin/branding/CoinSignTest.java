package mn.suld.plugin.branding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** UX-4: «₮» is real money (store prices, "₮5,000"); in-game coins read «зоос». */
class CoinSignTest {

    private static final Pattern COIN_AS_TUGRIK = Pattern.compile("₮(?![0-9])");

    @Test
    void theTugrikSignOnlyPrefixesRealMoneyPrices() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("../suld-api/src/main/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        if (COIN_AS_TUGRIK.matcher(lines.get(i)).find()) offenders.add(f.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        assertEquals(List.of(), offenders, "write in-game coins as «зоос»; ₮ only before a real-money amount");
    }
}
