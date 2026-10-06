package mn.suld.api.worldbuild;

/** A compile or validation finding. ERROR blocks a build; WARNING is reported. */
public record Issue(Severity severity, String code, String message, int x, int y, int z) {

    public enum Severity { ERROR, WARNING }

    public static Issue error(String code, String message, int x, int y, int z) {
        return new Issue(Severity.ERROR, code, message, x, y, z);
    }

    public static Issue warning(String code, String message, int x, int y, int z) {
        return new Issue(Severity.WARNING, code, message, x, y, z);
    }

    @Override
    public String toString() {
        return severity + " " + code + " @(" + x + "," + y + "," + z + "): " + message;
    }
}
