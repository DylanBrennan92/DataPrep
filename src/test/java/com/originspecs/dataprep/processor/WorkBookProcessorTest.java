package com.originspecs.dataprep.processor;

import com.originspecs.dataprep.model.RowData;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.model.WorkSheetData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WorkBookProcessor}.
 *
 * <p>All tests exercise the public {@code process()} pipeline through carefully
 * constructed {@link WorkSheetData} objects, so private implementation details
 * are verified entirely through observable output — headers and row data.
 *
 * <p><b>Column layout used throughout these tests (unless noted otherwise):</b>
 * <pre>
 *   Col 0 → 車名    (Car Name)   — protected from threshold filtering
 *   Col 1 → 通称名  (Common Name)
 *   Col 2 → 型式    (Model Type)
 *   Col 3 → エンジン (Engine)
 *   Col 4 → 重量    (Weight)
 *   Col 5 → 排気量  (Displacement)
 * </pre>
 *
 * <p>Data rows intentionally carry ≥ 4 non-empty cells so they are treated as
 * real data rows (not footnote/annotation rows) by the fill-rate logic.
 */
class WorkBookProcessorTest {

    /**
     * Permitted header map covering all labels used in test sheets.
     * Additional entries for "sparse" and "dense" column names used in threshold tests.
     */
    private static final Map<String, String> PERMITTED;
    static {
        var m = new LinkedHashMap<String, String>();
        m.put("車名", "Car Name");
        m.put("通称名", "Common Name");
        m.put("型式", "Model Type");
        m.put("エンジン", "Engine");
        m.put("原動機", "Engine");
        m.put("重量", "Weight");
        m.put("排気量", "Displacement");
        PERMITTED = m;
    }

    private WorkBookProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WorkBookProcessor(PERMITTED);
    }

    // -----------------------------------------------------------------------
    // Column threshold filtering
    // -----------------------------------------------------------------------

    @Nested
    class ColumnThresholdFiltering {

        @Test
        void columnWithZeroFill_isRemovedWhenBelowThreshold() {
            // Col 2 ("型式") is completely empty → fill = 0% → removed at threshold 0.01
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("スバル", "フォレスター", "",    "FB20", "1650kg", "2.0L"),
                    dataRow("",       "フォレスター", "",    "FB20", "1650kg", "2.0L"),
                    dataRow("",       "アウトバック",  "",    "FA24", "1840kg", "2.4L"),
                    dataRow("",       "アウトバック",  "",    "FA24", "1840kg", "2.4L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).doesNotContain("Model Type");
        }

        @Test
        void columnWithFullFill_isAlwaysKept() {
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("スバル", "フォレスター", "DBA-SK9", "FB20", "1650kg", "2.0L"),
                    dataRow("",       "フォレスター", "DBA-SK9", "FB20", "1650kg", "2.0L"),
                    dataRow("",       "アウトバック",  "CBA-BT1", "FA24", "1840kg", "2.4L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).contains("Model Type");
        }

        @Test
        void columnsBelowThresholdAreRemoved_columnsAboveThresholdAreKept() {
            // Col 2 (型式) has 0% fill — removed. Col 3 (エンジン) has 100% fill — kept.
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "カローラ", "", "1NZ-FE", "1180kg", "1.5L"),
                    dataRow("",       "カローラ", "", "1NZ-FE", "1180kg", "1.5L"),
                    dataRow("",       "ヤリス",   "", "1KR-FE", "940kg",  "1.0L"),
                    dataRow("",       "ヤリス",   "", "1KR-FE", "940kg",  "1.0L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.5);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).contains("Engine");
            assertThat(headers).doesNotContain("Model Type");
        }
    }

    // -----------------------------------------------------------------------
    // Car Name column protection
    // -----------------------------------------------------------------------

    @Nested
    class CarNameColumnProtection {

        @Test
        void carNameColumn_keptEvenWhenFillIsBelowThreshold() {
            // Col 0 (車名) has only 1 value across 5 rows = 20% fill.
            // With threshold 0.5 it would normally be removed — but it must be protected.
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("ホンダ", "フィット", "DBA-GK3", "L13B", "1080kg", "1.3L"),
                    dataRow("",       "フィット", "DBA-GK3", "L13B", "1080kg", "1.3L"),
                    dataRow("",       "フィット", "DBA-GK6", "L15B", "1120kg", "1.5L"),
                    dataRow("",       "シビック", "DAA-FK7", "L15C", "1290kg", "1.5L"),
                    dataRow("",       "シビック", "DAA-FK8", "K20C", "1380kg", "2.0L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.5);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).contains("Car Name");
        }

        @Test
        void carNameColumnNotInRawHeaders_noProtectionApplied_doesNotThrow() {
            // Sheet without 車名 in raw headers — processor should handle gracefully
            WorkSheetData sheet = buildSheet(
                    rawHeaders("通称名", "型式", "エンジン", "重量", "排気量", "燃費"),
                    dataRow("フィット", "DBA-GK3", "L13B", "1080kg", "1.3L", "23.4km/L"),
                    dataRow("フィット", "DBA-GK3", "L13B", "1080kg", "1.3L", "23.4km/L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.5);

            assertThat(result.getWorksheets().get(0).getHeaders()).isNotEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Duplicate header deduplication
    // -----------------------------------------------------------------------

    @Nested
    class DuplicateHeaderResolution {

        @Test
        void duplicateColumn_withSignificantlyLowerFill_isDropped() {
            // Col 1 (通称名) = 100% fill in data rows (real model names)
            // Col 2 (通称名) =  20% fill in data rows (sparse footnote artefact)
            // → col 2 is below 50% of col 1's fill and should be dropped
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "通称名", "エンジン", "重量", "排気量"),
                    dataRow("日産", "ノート",  "x",  "HR12DE", "1020kg", "1.2L"),  // col 2 has one entry
                    dataRow("",     "ノート",  "",   "HR12DE", "1020kg", "1.2L"),
                    dataRow("",     "リーフ",  "",   "EM57",   "1520kg", "0L"),
                    dataRow("",     "リーフ",  "",   "EM57",   "1520kg", "0L"),
                    dataRow("",     "リーフ",  "",   "EM57",   "1520kg", "0L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            // Only one Common Name column should survive
            long commonNameCount = headers.stream()
                    .filter(h -> h.equals("Common Name") || h.startsWith("Common Name ("))
                    .count();
            assertThat(commonNameCount).isEqualTo(1);
        }

        @Test
        void duplicateColumns_withSimilarFill_bothKeptWithNumberedSuffix() {
            // Col 1 and col 2 both resolve to "通称名" (Common Name) with equal fill
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "通称名", "エンジン", "重量", "排気量"),
                    dataRow("マツダ", "CX-5",  "CX5-A", "SH-VPTS", "1570kg", "2.2L"),
                    dataRow("",       "CX-5",  "CX5-B", "SH-VPTS", "1570kg", "2.2L"),
                    dataRow("",       "CX-30", "CX30-A", "PE-VPS", "1440kg", "2.0L"),
                    dataRow("",       "CX-30", "CX30-B", "PE-VPS", "1440kg", "2.0L"),
                    dataRow("",       "CX-30", "CX30-C", "PE-VPS", "1440kg", "2.0L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).contains("Common Name");
            // Both duplicate columns kept; (2) suffix stripped so we have "Common Name" twice
            assertThat(headers.stream().filter(h -> h.equals("Common Name")).count()).isEqualTo(2);
        }

        @Test
        void duplicateColumn_withFootnoteMarkers_isDropped() {
            // Col 1 = ※ (footnote marker), Col 2 = actual model names — both under 通称名 from merged header
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "通称名", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "※", "ピクシスエポ", "KF", "800kg", "0.66L"),
                    dataRow("",      "※", "ピクシスジョ", "KF", "800kg", "0.66L"),
                    dataRow("",      "※", "コペン",      "KF", "830kg", "0.66L"),
                    dataRow("",      "※", "ピクシスメガ", "KF", "900kg", "0.66L"),
                    dataRow("",      "※", "ピクシスメガ", "KF", "920kg", "0.66L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            long commonNameCount = headers.stream()
                    .filter(h -> h.equals("Common Name") || h.startsWith("Common Name ("))
                    .count();
            assertThat(commonNameCount).isEqualTo(1);
            int idx = headers.indexOf("Common Name");
            assertThat(result.getWorksheets().get(0).getRows().get(0).getCell(idx)).isEqualTo("ピクシスエポ");
        }

        @Test
        void duplicateColumn_withFootnoteMarkerVariants_isDropped() {
            // Col 1 = ※1 (footnote with number), Col 2 = actual model names — drop ※1, keep model names as "Common Name"
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "通称名", "エンジン", "重量", "排気量"),
                    dataRow("マツダ", "※1", "キャロル",   "KF", "800kg", "0.66L"),
                    dataRow("",      "※1", "フレア",     "KF", "800kg", "0.66L"),
                    dataRow("",      "※1", "フレア クロスオーバー", "KF", "830kg", "0.66L"),
                    dataRow("",      "※1", "フレア ワゴン", "KF", "900kg", "0.66L"),
                    dataRow("",      "※1", "スクラム",   "KF", "920kg", "0.66L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            assertThat(headers).contains("Common Name");
            assertThat(headers).doesNotContain("Common Name (2)");
            int idx = headers.indexOf("Common Name");
            assertThat(result.getWorksheets().get(0).getRows().get(0).getCell(idx)).isEqualTo("キャロル");
        }

        @Test
        void duplicateColumns_withIdenticalData_secondDropped() {
            // Col 2 and col 3 both have Engine header and identical data (KF, 0.658)
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "原動機", "原動機", "重量", "排気量"),
                    dataRow("トヨタ", "コペン", "KF", "KF", "830kg", "0.66L"),
                    dataRow("",      "コペン", "KF", "KF", "830kg", "0.66L"),
                    dataRow("",      "ピクシス", "KF", "KF", "800kg", "0.66L"),
                    dataRow("",      "ピクシス", "KF", "KF", "800kg", "0.66L"),
                    dataRow("",      "ピクシス", "KF", "KF", "800kg", "0.66L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            long engineCount = headers.stream()
                    .filter(h -> h.equals("Engine") || h.startsWith("Engine ("))
                    .count();
            assertThat(engineCount).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Fill-down — Car Name
    // -----------------------------------------------------------------------

    @Nested
    class FillDownCarName {

        @Test
        void carName_filledToAllDataRows() {
            // Only first row has "スバル" in the Car Name column — rest are blank
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("スバル", "フォレスター", "DBA-SK9", "FB20", "1650kg", "2.0L"),
                    dataRow("",       "フォレスター", "DBA-SK9", "FB20", "1680kg", "2.0L"),
                    dataRow("",       "アウトバック",  "CBA-BT1", "FA24", "1840kg", "2.4L"),
                    dataRow("",       "アウトバック",  "CBA-BT1", "FA24", "1870kg", "2.4L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();

            int carNameIdx = result.getWorksheets().get(0).getHeaders().indexOf("Car Name");
            assertThat(carNameIdx).isNotNegative();
            assertThat(rows).allSatisfy(row ->
                    assertThat(row.getCell(carNameIdx)).isEqualTo("スバル")
            );
        }

        @Test
        void carName_doesNotOverwriteExistingNonEmptyValue() {
            // If a car name already exists (e.g. a different brand row), it must not be overwritten
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "カローラ", "DBA-ZRE212", "2ZR-FAE", "1290kg", "1.8L"),
                    dataRow("",       "カローラ", "DBA-ZRE212", "2ZR-FAE", "1320kg", "1.8L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();

            int carNameIdx = result.getWorksheets().get(0).getHeaders().indexOf("Car Name");
            assertThat(rows.get(0).getCell(carNameIdx)).isEqualTo("トヨタ");
            assertThat(rows.get(1).getCell(carNameIdx)).isEqualTo("トヨタ");
        }
    }

    // -----------------------------------------------------------------------
    // Fill-down — Common Name
    // -----------------------------------------------------------------------

    @Nested
    class FillDownCommonName {

        @Test
        void commonName_filledDownUntilNextDistinctValue() {
            // "カローラ" appears in row 0, "ヤリス" in row 2 — rows 1 and 3 are blank
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "カローラ", "ZRE212", "2ZR", "1290kg", "1.8L"),
                    dataRow("",       "",         "ZRE212", "2ZR", "1320kg", "1.8L"),  // blank → fill カローラ
                    dataRow("",       "ヤリス",   "KSP210", "1KR", "940kg",  "1.0L"),  // new model
                    dataRow("",       "",         "KSP210", "1KR", "960kg",  "1.0L")   // blank → fill ヤリス
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            int commonIdx = headers.indexOf("Common Name");
            assertThat(commonIdx).isNotNegative();
            assertThat(rows.get(0).getCell(commonIdx)).isEqualTo("カローラ");
            assertThat(rows.get(1).getCell(commonIdx)).isEqualTo("カローラ");  // filled down
            assertThat(rows.get(2).getCell(commonIdx)).isEqualTo("ヤリス");
            assertThat(rows.get(3).getCell(commonIdx)).isEqualTo("ヤリス");    // filled down
        }

        @Test
        void fillDown_doesNotExtendPastLastDataRow_andTrailingFootnoteIsRemoved() {
            // The last row has only 1 non-empty cell — it is a footnote row. Fill-down must not
            // extend to it, and removeTrailingData must remove it from the output.
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("スズキ", "スイフト", "ZC33S", "K14C", "940kg",  "1.4L"),
                    dataRow("",       "スイフト", "ZC33S", "K14C", "940kg",  "1.4L"),
                    dataRow("",       "ジムニー", "JB64W", "R06A", "1070kg", "0.66L"),
                    dataRow("",       "",         "JB64W", "R06A", "1100kg", "0.66L"),
                    footnoteRow("(注）スズキ株式会社")  // 1 non-empty cell → footnote, removed by trailing-data step
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();
            List<String> headers = result.getWorksheets().get(0).getHeaders();

            int carNameIdx  = headers.indexOf("Car Name");
            int commonIdx   = headers.indexOf("Common Name");

            // Footnote row is removed — output has only the 4 data rows
            assertThat(rows).hasSize(4);
            RowData lastRow = rows.get(rows.size() - 1);
            assertThat(lastRow.getCell(carNameIdx)).isEqualTo("スズキ");
            assertThat(lastRow.getCell(commonIdx)).isEqualTo("ジムニー");
        }
    }

    // -----------------------------------------------------------------------
    // Trailing data removal
    // -----------------------------------------------------------------------

    @Nested
    class TrailingDataRemoval {

        @Test
        void footnoteRowAfterData_isRemoved() {
            // Real data rows (≥4 cells) followed by a footnote (注) row with only 1 non-empty cell
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("レクサス", "RX450h", "2GR", "3.456", "19.4", "120"),
                    dataRow("", "RX270", "1AR", "2.671", "10.4", "223"),
                    footnoteRow("(注) JC08モード燃費値を有する車両については、10・15モード燃費値に下線を引いています。")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getCell(1)).isEqualTo("RX450h");
            assertThat(rows.get(1).getCell(1)).isEqualTo("RX270");
        }

        @Test
        void blankRowsAfterData_areRemoved() {
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "カローラ", "ZRE212", "2ZR", "1290kg", "1.8L"),
                    dataRow("", "", "", "", "", ""),
                    dataRow("", "", "", "", "", "")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getCell(1)).isEqualTo("カローラ");
        }

        @Test
        void noTrailingData_rowsUnchanged() {
            WorkSheetData sheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("ホンダ", "フィット", "GK3", "L13B", "1080kg", "1.3L"),
                    dataRow("", "フィット", "GK6", "L15B", "1120kg", "1.5L")
            );

            WorkBookData result = processor.process(workBook(sheet), 0.01);
            List<RowData> rows = result.getWorksheets().get(0).getRows();

            assertThat(rows).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // Multi-sheet processing
    // -----------------------------------------------------------------------

    @Nested
    class MultiSheetProcessing {

        @Test
        void multipleSheets_eachProcessedIndependently() {
            WorkSheetData toyotaSheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("トヨタ", "カローラ", "ZRE212", "2ZR", "1290kg", "1.8L"),
                    dataRow("",       "カローラ", "ZRE212", "2ZR", "1320kg", "1.8L")
            );
            toyotaSheet.setName("Toyota");

            WorkSheetData hondaSheet = buildSheet(
                    rawHeaders("車名", "通称名", "型式", "エンジン", "重量", "排気量"),
                    dataRow("ホンダ", "フィット", "GK3", "L13B", "1080kg", "1.3L"),
                    dataRow("",       "フィット", "GK3", "L13B", "1100kg", "1.3L")
            );
            hondaSheet.setName("Honda");

            WorkBookData result = processor.process(workBook(toyotaSheet, hondaSheet), 0.01);

            assertThat(result.getWorksheets()).hasSize(2);

            List<String> toyotaHeaders = result.getWorksheets().get(0).getHeaders();
            List<String> hondaHeaders  = result.getWorksheets().get(1).getHeaders();

            assertThat(toyotaHeaders).contains("Car Name", "Common Name");
            assertThat(hondaHeaders).contains("Car Name", "Common Name");

            int carNameIdx = toyotaHeaders.indexOf("Car Name");
            assertThat(result.getWorksheets().get(0).getRows().get(1).getCell(carNameIdx))
                    .isEqualTo("トヨタ");
            assertThat(result.getWorksheets().get(1).getRows().get(1)
                    .getCell(hondaHeaders.indexOf("Car Name")))
                    .isEqualTo("ホンダ");
        }
    }

    // -----------------------------------------------------------------------
    // Test data builders
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link WorkSheetData} from a single raw header row and a list of data rows.
     * {@code originalColumnCount} is derived from the header row width.
     */
    private static WorkSheetData buildSheet(List<String> headerRow, RowData... dataRows) {
        WorkSheetData sheet = new WorkSheetData();
        sheet.setName("test-sheet");
        sheet.setRawHeaderRows(List.of(headerRow));
        sheet.setRows(Arrays.asList(dataRows));
        sheet.setOriginalColumnCount(headerRow.size());
        sheet.setOriginalRowCount(dataRows.length);
        return sheet;
    }

    private static List<String> rawHeaders(String... values) {
        return Arrays.asList(values);
    }

    /**
     * Creates a data row where every cell is populated (6 columns ≥ DATA_ROW_MIN_CELLS = 4).
     */
    private static RowData dataRow(String... values) {
        return new RowData(Arrays.asList(values));
    }

    /**
     * Creates a footnote row where the note text sits in col 2 (a body/spec column), leaving
     * the Car Name (col 0) and Common Name (col 1) cells empty. This mirrors real MLIT XLS
     * files where footnotes like "(注）..." span a single body column after the last data row.
     *
     * <p>With only 1 non-empty cell the row falls below DATA_ROW_MIN_CELLS (4) and is treated
     * as an annotation row — fill-down must not touch it.
     */
    private static RowData footnoteRow(String noteText) {
        return new RowData(Arrays.asList("", "", noteText, "", "", ""));
    }

    private static WorkBookData workBook(WorkSheetData... sheets) {
        WorkBookData wb = new WorkBookData();
        wb.setFileName("test.xls");
        wb.setWorksheetCount(sheets.length);
        for (WorkSheetData sheet : sheets) {
            wb.getWorksheets().add(sheet);
        }
        return wb;
    }
}
