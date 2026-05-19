package com.siruoren.buildparameterhistory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildParameterRecordTest {

    @Test
    @DisplayName("Constructor: 全参数构造函数应正确设置所有字段")
    void fullConstructorShouldSetAllFields() {
        List<BuildParameterRecord.ParameterEntry> params = new ArrayList<>();
        params.add(new BuildParameterRecord.ParameterEntry("BRANCH", "main"));
        params.add(new BuildParameterRecord.ParameterEntry("ENV", "prod"));

        BuildParameterRecord record = new BuildParameterRecord(
                "test-job", "42", "/job/test-job/42",
                1700000000000L, 1700000060000L, "SUCCESS", params);

        assertEquals("test-job", record.getJobName());
        assertEquals("42", record.getBuildId());
        assertEquals("/job/test-job/42", record.getBuildUrl());
        assertEquals(1700000000000L, record.getStartTime());
        assertEquals(1700000060000L, record.getEndTime());
        assertEquals("SUCCESS", record.getResult());
        assertEquals(2, record.getParameters().size());
    }

    @Test
    @DisplayName("Default constructor: 应初始化空参数列表")
    void defaultConstructorShouldInitEmptyParams() {
        BuildParameterRecord record = new BuildParameterRecord();
        assertNotNull(record.getParameters());
        assertTrue(record.getParameters().isEmpty());
        assertNull(record.getJobName());
        assertNull(record.getResult());
    }

    @Test
    @DisplayName("Constructor with null parameters: 应创建空列表而非NPE")
    void nullParametersShouldBecomeEmptyList() {
        BuildParameterRecord record = new BuildParameterRecord(
                "test-job", "1", "/", 100L, 200L, null, null);
        assertNotNull(record.getParameters());
        assertTrue(record.getParameters().isEmpty());
    }

    // ========== Getters/Setters ==========

    @Test
    @DisplayName("Getters and Setters: 字段读写应正确")
    void getterSetterTest() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setJobName("new-job");
        record.setBuildId("99");
        record.setBuildUrl("/job/new/99");
        record.setStartTime(9999L);
        record.setEndTime(8888L);
        record.setResult("FAILURE");

        assertEquals("new-job", record.getJobName());
        assertEquals("99", record.getBuildId());
        assertEquals("/job/new/99", record.getBuildUrl());
        assertEquals(9999L, record.getStartTime());
        assertEquals(8888L, record.getEndTime());
        assertEquals("FAILURE", record.getResult());
    }

    @Test
    @DisplayName("setParameters(null): 应替换为空列表")
    void setNullParametersShouldReplaceWithEmptyList() {
        BuildParameterRecord record = new BuildParameterRecord();
        List<BuildParameterRecord.ParameterEntry> params = new ArrayList<>();
        params.add(new BuildParameterRecord.ParameterEntry("key", "val"));
        record.setParameters(params);
        assertEquals(1, record.getParameters().size());

        record.setParameters(null);
        assertNotNull(record.getParameters());
        assertTrue(record.getParameters().isEmpty());
    }

    // ========== ParameterEntry ==========

    @Test
    @DisplayName("ParameterEntry: 构造和getter应正确")
    void parameterEntryConstructorAndGetters() {
        BuildParameterRecord.ParameterEntry entry = new BuildParameterRecord.ParameterEntry("GIT_BRANCH", "develop");
        assertEquals("GIT_BRANCH", entry.getName());
        assertEquals("develop", entry.getValue());
    }

    @Test
    @DisplayName("ParameterEntry toString: 应包含名称和值")
    void parameterEntryToString() {
        BuildParameterRecord.ParameterEntry entry = new BuildParameterRecord.ParameterEntry("KEY", "value");
        String str = entry.toString();
        assertNotNull(str);
        assertTrue(str.contains("KEY"));
        assertTrue(str.contains("value"));
    }

    // ========== Safe URL ==========

    @Test
    @DisplayName("getSafeBuildUrl: 正常URL应原样返回")
    void safeUrlNormalCase() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("/job/my-job/10/");
        assertEquals("/job/my-job/10/", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: 空URL应返回空字符串")
    void safeUrlEmptyOrNull() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("");
        assertEquals("", record.getSafeBuildUrl());

        record.setBuildUrl(null);
        assertEquals("", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: javascript协议应被阻止返回空字符串")
    void safeUrlJavascriptInjection() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("javascript:alert('xss')");
        assertEquals("", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: data协议应被阻止")
    void safeUrlDataProtocol() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("data:text/html,<h1>xss</h1>");
        assertEquals("", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: vbscript协议应被阻止")
    void safeUrlVbscriptProtocol() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("vbscript:msgbox('xss')");
        assertEquals("", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: 大写JAVASCRIPT协议应也被阻止")
    void safeUrlUppercaseJavascript() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("JavaScript:alert(1)");
        assertEquals("", record.getSafeBuildUrl());
    }

    @Test
    @DisplayName("getSafeBuildUrl: 带空格的URL应去除前后空白")
    void safeUrlTrimmed() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setBuildUrl("  /job/test/1  ");
        assertEquals("/job/test/1", record.getSafeBuildUrl());
    }

    // ========== Duration ==========

    @Test
    @DisplayName("getDuration: 正常时间差应计算时长")
    void durationNormal() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setStartTime(1000L);
        record.setEndTime(60000L);  // 59秒差值
        String duration = record.getDuration();
        assertNotNull(duration);
        assertNotEquals("N/A", duration);
    }

    @Test
    @DisplayName("getDuration: startTime或endTime为0时应返回N/A")
    void durationZeroTimes() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setStartTime(0L);
        record.setEndTime(5000L);
        assertEquals("N/A", record.getDuration());

        record.setStartTime(5000L);
        record.setEndTime(0L);
        assertEquals("N/A", record.getDuration());

        record.setStartTime(0L);
        record.setEndTime(0L);
        assertEquals("N/A", record.getDuration());
    }

    @Test
    @DisplayName("getDuration: endTime < startTime时也应能处理（负差值）")
    void durationNegativeDifference() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setStartTime(5000L);
        record.setEndTime(3000L);
        String duration = record.getDuration();
        assertNotNull(duration);
    }

    // ========== Formatted Time ==========

    @Test
    @DisplayName("getFormattedStartTime: 有效时间戳应格式化")
    void formattedStartTimeValid() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setStartTime(1700000000000L); // 2023-11-14 某时间点
        String formatted = record.getFormattedStartTime();
        assertNotNull(formatted);
        assertFalse(formatted.equals("N/A"));
        assertTrue(formatted.contains("2023"));
    }

    @Test
    @DisplayName("getFormattedStartTime: 时间戳为0时应返回N/A")
    void formattedStartTimeZero() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setStartTime(0L);
        assertEquals("N/A", record.getFormattedStartTime());
    }

    @Test
    @DisplayName("getFormattedEndTime: 有效时间戳应格式化")
    void formattedEndTimeValid() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setEndTime(1700100000000L);
        String formatted = record.getFormattedEndTime();
        assertNotNull(formatted);
        assertFalse(formatted.equals("N/A"));
    }

    @Test
    @DisplayName("getFormattedEndTime: 时间戳为0时应返回N/A")
    void formattedEndTimeZero() {
        BuildParameterRecord record = new BuildParameterRecord();
        record.setEndTime(0L);
        assertEquals("N/A", record.getFormattedEndTime());
    }
}
