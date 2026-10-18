package mn.suld.api.hologram;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything in content/holograms.json: the boards in file order and the labels by id. */
public record HologramSet(List<HologramBoard> boards, Map<String, HologramLabel> labels) {

    public HologramSet {
        boards = List.copyOf(boards);
        labels = Map.copyOf(labels);
    }

    public Optional<HologramBoard> board(String id) {
        for (HologramBoard b : boards) if (b.id().equals(id)) return Optional.of(b);
        return Optional.empty();
    }

    public Optional<HologramLabel> label(String id) {
        return Optional.ofNullable(labels.get(id));
    }
}
