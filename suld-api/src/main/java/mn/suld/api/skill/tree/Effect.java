package mn.suld.api.skill.tree;

import mn.suld.api.skill.Spell;

import java.util.Objects;

/** One thing a node does. Pure data: the plugin's effect engine interprets it. */
public sealed interface Effect {

    /** A permanent stat. */
    record Stat(StatKey key, double value) implements Effect {
        public Stat {
            Objects.requireNonNull(key, "key");
        }
    }

    /** A change to one spell. */
    record SpellMod(Spell spell, ModKey key, double value) implements Effect {
        public SpellMod {
            Objects.requireNonNull(spell, "spell");
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * A passive spell: on {@code event}, with {@code chance} percent, do {@code kind}; then wait
     * {@code cooldown} seconds before it can fire again.
     */
    record Proc(TriggerEvent event, double chance, ProcKind kind, double a, double b, double cooldown) implements Effect {
        public Proc {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(kind, "kind");
            chance = Math.max(0, Math.min(100, chance));
            cooldown = Math.max(0, cooldown);
        }
    }

    /** Learn an ultimate (cast with F). Only one can be learned: the three are mutually exclusive. */
    record UnlockUltimate(Ultimate ultimate) implements Effect {
        public UnlockUltimate {
            Objects.requireNonNull(ultimate, "ultimate");
        }
    }

    /** A keystone: a rule change with a price, interpreted by the effect engine. */
    record Keystone(KeystoneKind kind) implements Effect {
        public Keystone {
            Objects.requireNonNull(kind, "kind");
        }
    }

    static Effect keystone(KeystoneKind kind) { return new Keystone(kind); }

    static Effect stat(StatKey key, double value) { return new Stat(key, value); }

    static Effect mod(Spell spell, ModKey key, double value) { return new SpellMod(spell, key, value); }

    static Effect proc(TriggerEvent event, double chance, ProcKind kind, double a, double b, double cooldown) {
        return new Proc(event, chance, kind, a, b, cooldown);
    }

    static Effect ult(Ultimate ultimate) { return new UnlockUltimate(ultimate); }
}
