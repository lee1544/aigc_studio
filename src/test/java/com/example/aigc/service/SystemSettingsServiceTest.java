package com.example.aigc.service;

import com.example.aigc.entity.ops.SystemSetting;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** J08：设置字段类型/范围校验、默认值、未传保留、features 忽略、保存后 bump 配置版本。 */
@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock private SystemSettingRepository repository;
    @Mock private ConfigVersionService configVersionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SystemSettingsService service;
    private SystemSetting row;

    @BeforeEach
    void setUp() {
        row = new SystemSetting();
        row.setId(1);
        row.setData("{\"platformName\":\"已有名称\",\"retentionDays\":90}");
        lenient().when(repository.findById(1)).thenReturn(Optional.of(row));

        service = new SystemSettingsService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "configVersionService", configVersionService);
    }

    @Test
    void defaultsCoverFullContractWithoutFeatures() throws Exception {
        JsonNode d = service.defaults();
        assertEquals(8, d.size());
        assertFalse(d.has("features"));
        assertEquals("480P", d.get("defaultVideoResolution").asText());
        assertEquals(15, d.get("maxVideoDuration").asInt());
        assertEquals(4, d.get("taskConcurrency").asInt());
        assertEquals(24, d.get("jwtHours").asInt());
        assertEquals(30, d.get("retentionDays").asInt());
    }

    @Test
    void readMergesStoredValueOverDefaults() {
        JsonNode current = service.get();
        assertEquals("已有名称", current.get("platformName").asText());
        assertEquals(90, current.get("retentionDays").asInt());
        // 未保存过的字段回落到默认值
        assertEquals("480P", current.get("defaultVideoResolution").asText());
    }

    @Test
    void putKeepsUntouchedFieldsAndBumpsVersion() throws Exception {
        JsonNode body = objectMapper.readTree("{\"platformName\":\"新平台\",\"taskConcurrency\":8}");
        JsonNode saved = service.put(body);
        assertEquals("新平台", saved.get("platformName").asText());
        assertEquals(8, saved.get("taskConcurrency").asInt());
        // 未传字段保留原值
        assertEquals(90, saved.get("retentionDays").asInt());
        verify(configVersionService).bump();
    }

    @Test
    void putIgnoresFeaturesFlag() throws Exception {
        service.put(objectMapper.readTree("{\"features\":{\"script\":true}}"));
        assertFalse(row.getData().contains("features"));
    }

    @Test
    void unknownFieldIsRejected() {
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"siteName\":\"x\"}")));
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"taskConcurrency\":21}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"retentionDays\":0}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"maxVideoDuration\":7}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"jwtHours\":12}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"defaultVideoResolution\":\"4K\"}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"platformName\":\"\"}")));
    }

    @Test
    void wrongTypesAreRejected() {
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"allowRegistration\":\"yes\"}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"retentionDays\":\"30\"}")));
        assertThrows(BusinessException.class, () -> service.put(objectMapper.readTree("{\"platformName\":123}")));
    }

    @Test
    void publicSettingsMatchesAdminView() {
        assertTrue(service.publicSettings().isObject());
        assertFalse(service.publicSettings().has("features"));
    }
}
