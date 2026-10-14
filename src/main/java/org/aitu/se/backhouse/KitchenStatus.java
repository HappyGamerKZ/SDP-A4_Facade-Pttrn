package org.aitu.se.backhouse;

public enum KitchenStatus {
    PENDING_STATION, // waiting for free station
    PREPARING,       // cooking
    QUALITY_PASSED,  // cooked and checked
    WASTED           // wasted or canceled
}
