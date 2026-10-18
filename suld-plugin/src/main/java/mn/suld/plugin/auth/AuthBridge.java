package mn.suld.plugin.auth;

import org.bukkit.entity.Player;

/** What SÜLD asks the plugin that logs players in (AuthMe). Every answer is "no" when the plugin cannot be asked. */
public interface AuthBridge {

    /** No login plugin: nobody is ever logged in through it. */
    AuthBridge NONE = new AuthBridge() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isAuthenticated(Player p) {
            return false;
        }

        @Override
        public boolean isRegistered(String name) {
            return false;
        }

        @Override
        public String describe() {
            return "none";
        }
    };

    /** The login plugin is there and answers. */
    boolean available();

    boolean isAuthenticated(Player p);

    /** Whether an account (password or premium) exists for {@code name}. */
    boolean isRegistered(String name);

    /** One line for {@code /suld auth} and the log; never a secret. */
    String describe();

    /** Called on the server thread when the login plugin logs a player in or out. */
    interface Events {
        void loggedIn(Player p);

        void loggedOut(Player p);
    }
}
