package krd.parliament.vms.domain;

/** Waiting list priority; lower rank is served first. */
public enum Priority {
    VIP(1, "danger"),
    OFFICIAL(2, "primary"),
    URGENT_HUMANITARIAN(3, "warning"),
    NORMAL(4, "secondary");

    private final int rank;
    private final String color;

    Priority(int rank, String color) {
        this.rank = rank;
        this.color = color;
    }

    public int getRank() {
        return rank;
    }

    public String getColor() {
        return color;
    }
}
