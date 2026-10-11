package mn.suld.api.campaign;

/** Lifecycle of one week's campaign; the database CHECK constraint lists the same names. */
public enum CampaignState {
    SCHEDULED, ACTIVE, SUCCEEDED, FAILED, CANCELLED;

    public boolean resolved() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
