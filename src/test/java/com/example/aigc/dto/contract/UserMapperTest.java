package com.example.aigc.dto.contract;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMapperTest {

    @Test
    void isoConvertsLocalWallClockToUtc() {
        // 数据库存的是本地墙上时间；接口必须输出同一时刻的 UTC 表示，而不是直接贴 Z
        LocalDateTime wallClock = LocalDateTime.of(2026, 11, 5, 14, 45, 25);
        String iso = UserMapper.iso(wallClock);
        OffsetDateTime parsed = OffsetDateTime.parse(iso);

        assertEquals("Z", parsed.getOffset().getId());
        assertEquals(wallClock.atZone(ZoneId.systemDefault()).toInstant(), parsed.toInstant());
    }

    @Test
    void isoKeepsNullAsNull() {
        assertNull(UserMapper.iso(null));
    }
}
