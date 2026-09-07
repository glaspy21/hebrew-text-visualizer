package com.hebrewproject.service;

/**
 * The result of walking a Strong's ID's derivation chain: the Strong's ID it
 * resolved to, and whether the walk was stopped early because the chain went
 * deeper than the depth cap (flagged = true means "don't trust this as fully
 * resolved" - see RootDerivationResolver and the Elohim case in PROJECT_NOTES.md).
 */
public class RootResolution {

    private final String rootStrongId;
    private final boolean flagged;

    public RootResolution(String rootStrongId, boolean flagged) {
        this.rootStrongId = rootStrongId;
        this.flagged = flagged;
    }

    public String getRootStrongId() { return rootStrongId; }
    public boolean isFlagged() { return flagged; }
}
