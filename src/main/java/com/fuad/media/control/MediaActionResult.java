package com.fuad.media.control;

public record MediaActionResult(
  MediaAction action,
  MediaActionStatus status) {

    public boolean successful() {
        return status == MediaActionStatus.ACKNOWLEDGED;
    }
}
