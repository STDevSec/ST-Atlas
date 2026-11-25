package com.stdevsec.atlas.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellTest {

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(TimeTrackingService.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(TimeTrackingService.cell("+1")).isEqualTo("\"'+1\"");
        assertThat(TimeTrackingService.cell("Recon")).isEqualTo("\"Recon\"");
    }
}
