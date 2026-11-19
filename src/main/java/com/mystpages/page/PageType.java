package com.mystpages.page;

/** The generation types MystPages can create pages for. */
public enum PageType {
    ORE("ore"),
    FLUID("fluid"),
    STRUCTURE("structure");

    private final String commandName;

    PageType(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static PageType fromCommandName(String name) {
        for (PageType type : values()) {
            if (type.commandName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
