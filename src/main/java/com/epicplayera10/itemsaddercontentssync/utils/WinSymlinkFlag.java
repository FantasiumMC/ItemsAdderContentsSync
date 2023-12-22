package com.epicplayera10.itemsaddercontentssync.utils;

public enum WinSymlinkFlag {
    SOFT_LINK("/D"),
    HARD_LINK("/H"),
    JUNCTION("/J");

    public final String rawFlag;

    WinSymlinkFlag(String rawFlag) {
        this.rawFlag = rawFlag;
    }
}
