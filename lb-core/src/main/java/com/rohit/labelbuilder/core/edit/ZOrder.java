package com.rohit.labelbuilder.core.edit;

/**
 * A z-order move. The document's element list is ordered back-to-front (lbl-format.md §3), so "front"
 * means the end of the list.
 */
public enum ZOrder {
    BRING_TO_FRONT("Bring to Front"),
    BRING_FORWARD("Bring Forward"),
    SEND_BACKWARD("Send Backward"),
    SEND_TO_BACK("Send to Back");

    private final String displayName;

    ZOrder(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
