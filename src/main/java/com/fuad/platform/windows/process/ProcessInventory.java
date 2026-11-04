package com.fuad.platform.windows.process;

@FunctionalInterface
public interface ProcessInventory {
    ProcessInventorySnapshot snapshot();
}
