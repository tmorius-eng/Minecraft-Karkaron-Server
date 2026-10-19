package mn.suld.plugin.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts that need no server: the JSON body and the rule for which URLs a webhook may be. */
class DiscordNotifierTest {

    @Test
    void theBodyIsValidJsonWhateverTheLineHolds() {
        assertEquals("{\"content\":\"a \\\"b\\\" \\\\ c\\nd\\te\\u0001 Хуралдай ✦\"}", DiscordNotifier.json("a \"b\" \\ c\nd\te\u0001 Хуралдай ✦"));
    }

    @Test
    void onlyHttpsOrALoopbackHttpUrlIsAccepted() {
        assertTrue(DiscordNotifier.acceptable("https://discord.com/api/webhooks/1/abc"));
        assertTrue(DiscordNotifier.acceptable("http://127.0.0.1:8089/suld"), "a dev server's local stand-in");
        assertTrue(DiscordNotifier.acceptable("http://localhost:8089/suld"));
        assertFalse(DiscordNotifier.acceptable("http://discord.com/api/webhooks/1/abc"), "a secret never travels in clear text");
        assertFalse(DiscordNotifier.acceptable("http://127.0.0.1.evil.example/hook"));
        assertFalse(DiscordNotifier.acceptable("ftp://x"));
        assertFalse(DiscordNotifier.acceptable(""));
        assertFalse(DiscordNotifier.acceptable(null));
    }
}
