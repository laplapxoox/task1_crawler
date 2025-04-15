package com.dantri.webcrawler;

public enum Domain {
    DANTRI("dantri"),
    VNEXPRESS("vnexpress"),
    VIETNAMNET("vietnamnet"),
    THANHNIEN("thanhnien"),
    TUOITRE("tuoitre");

    private final String name;

    Domain(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Domain fromName(String name) {
        for (Domain domain : values()) {
            if (domain.name.equalsIgnoreCase(name)) {
                return domain;
            }
        }
        throw new IllegalArgumentException("Unknown domain: " + name);
    }
}
