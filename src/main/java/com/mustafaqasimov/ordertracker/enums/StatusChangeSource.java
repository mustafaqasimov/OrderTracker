package com.mustafaqasimov.ordertracker.enums;

public enum StatusChangeSource {
    USER,
    ADMIN,
    WEBHOOK_PAYMENT,
    WEBHOOK_SHIPMENT,
    SYSTEM
}