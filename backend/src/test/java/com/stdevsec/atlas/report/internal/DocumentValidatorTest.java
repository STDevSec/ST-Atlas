package com.stdevsec.atlas.report.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stdevsec.atlas.shared.BadRequestException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DocumentValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void collectsAttachmentAndFindingReferences() {
        var r = DocumentValidator.validate("""
                {"type":"doc","content":[
                 {"type":"image","attrs":{"attachmentId":"0190a000-0000-7000-8000-000000000001"}},
                 {"type":"findingRef","attrs":{"findingId":"0190a000-0000-7000-8000-000000000002"}}]}""", JSON);
        assertThat(r.attachmentIds()).hasSize(1);
        assertThat(r.findingIds()).hasSize(1);
    }

    @Test
    void rejectsBase64Images() {
        assertThatThrownBy(() -> DocumentValidator.validate("""
                {"type":"doc","content":[{"type":"image","attrs":{"src":"data:image/png;base64,AAAA",
                 "attachmentId":"0190a000-0000-7000-8000-000000000001"}}]}""", JSON))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownNodesMarksAndUnsafeLinks() {
        assertThatThrownBy(() -> DocumentValidator.validate("{\"type\":\"doc\",\"content\":[{\"type\":\"script\"}]}", JSON))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DocumentValidator.validate("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x",
                 "marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}]}""", JSON))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DocumentValidator.validate("{\"type\":\"paragraph\"}", JSON))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsExcessiveNesting() {
        String deep = "{\"type\":\"blockquote\",\"content\":[".repeat(60) + "{\"type\":\"paragraph\"}" + "]}".repeat(60);
        assertThatThrownBy(() -> DocumentValidator.validate("{\"type\":\"doc\",\"content\":[" + deep + "]}", JSON))
                .isInstanceOf(BadRequestException.class);
    }
}
