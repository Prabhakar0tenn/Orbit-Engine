package com.prabhakar.orbit.domain;

public final class ReadyShard {

    private ReadyShard() {
    }

    public static String name(int index) {
        return "READY#" + index;
    }
}
