package com.stdevsec.atlas.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Typed template configuration. There is deliberately no free-form HTML, CSS or script:
 * every field is a constrained value the renderer turns into markup itself (T-12, T-14).
 * Text fields may contain {{variables}} from the fixed allow-list in {@code Variables}.
 */
public record TemplateConfig(
        @NotNull @Size(max = 200) String title,
        @Size(max = 300) String subtitle,
        @NotNull PaperSize paperSize,
        @NotNull Orientation orientation,
        @NotNull @Valid Margins margins,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String primaryColor,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String accentColor,
        @NotNull FontFamily fontFamily,
        @Min(9) @Max(14) int baseFontSizePt,
        boolean showCover,
        boolean showOrganizationLogo,
        boolean showClientLogo,
        UUID organizationLogoAttachmentId,
        @NotNull LogoPosition logoPosition,
        @Size(max = 200) String headerText,
        @Size(max = 200) String footerText,
        boolean pageNumbers,
        @NotNull Classification classification,
        @Size(max = 40) String watermarkText,
        boolean showFindingsSummary,
        boolean showCvss) {

    public enum PaperSize { A4, LETTER }

    public enum Orientation { PORTRAIT, LANDSCAPE }

    public enum FontFamily { SANS, SERIF }

    public enum LogoPosition { LEFT, CENTER, RIGHT }

    public enum Classification { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED }

    public record Margins(@Min(5) @Max(50) int topMm, @Min(5) @Max(50) int rightMm, @Min(5) @Max(50) int bottomMm,
            @Min(5) @Max(50) int leftMm) {}

    public static TemplateConfig standard() {
        return new TemplateConfig("{{assessment.title}}", "{{assessment.type}} — {{client.name}}", PaperSize.A4,
                Orientation.PORTRAIT, new Margins(22, 18, 22, 18), "#1F3A5F", "#3B82F6", FontFamily.SANS, 10, true,
                true, true, null, LogoPosition.LEFT, "{{organization.name}} · {{assessment.title}}",
                "{{report.classification}} · v{{report.version}}", true, Classification.CONFIDENTIAL, null, true, true);
    }
}
