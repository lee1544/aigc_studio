package com.example.aigc.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FeaturePermissionsTest {

    @Test
    void lyricRecognitionIsNoLongerACreatorFeature() {
        assertEquals(25, FeaturePermissions.CODES.size());
        assertFalse(FeaturePermissions.isKnown("lyricRecognition"));
        assertFalse(FeaturePermissions.allFalse().containsKey("lyricRecognition"));
    }
}
