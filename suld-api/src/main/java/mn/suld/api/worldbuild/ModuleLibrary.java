package mn.suld.api.worldbuild;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of modules by id (procedural Java modules and data blueprints alike). */
public final class ModuleLibrary {

    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleLibrary register(Module m) {
        if (modules.putIfAbsent(m.id(), m) != null) throw new IllegalArgumentException("duplicate module id " + m.id());
        return this;
    }

    public Optional<Module> find(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public Module get(String id) {
        Module m = modules.get(id);
        if (m == null) throw new IllegalArgumentException("unknown module " + id);
        return m;
    }

    public Collection<Module> all() {
        return modules.values();
    }
}
