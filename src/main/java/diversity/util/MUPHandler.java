package diversity.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class MUPHandler {

    private final List<BitSet> mups = new ArrayList<>();

    public void addMUPs(List<BitSet> new_mups) {
        if (!new_mups.isEmpty()) {
            this.mups.addAll(new_mups);
        }
    }

    public List<BitSet> getMUPs() {
        return mups;
    }
}
