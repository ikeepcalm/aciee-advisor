package ro.aciee.advisor.domain.report.service;

import org.dhatim.fastexcel.Workbook;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.report.model.ReportTable;
import ro.aciee.advisor.domain.report.model.source.ReportFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ReportFileWriter {

    public byte[] write(ReportTable table, ReportFormat format) {
        var out = new ByteArrayOutputStream();
        try {
            switch (format) {
                case CSV -> csv(table, out);
                case XLSX -> xlsx(table, out);
                case PDF -> pdf(table, out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private void csv(ReportTable table, OutputStream out) throws IOException {
        var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write('﻿');
        writer.write(csvLine(table.getColumns()));
        for (var row : table.getRows()) {
            writer.write(csvLine(row));
        }
        writer.flush();
    }

    private String csvLine(List<?> values) {
        return values.stream()
                .map(value -> switch (value) {
                    case null -> "";
                    case String text -> "\"" + (text.matches("^[=+\\-@].*") ? "'" : "") + text.replace("\"", "\"\"") + "\"";
                    default -> value.toString();
                })
                .collect(Collectors.joining(",", "", "\r\n"));
    }

    private void xlsx(ReportTable table, OutputStream out) throws IOException {
        try (var workbook = new Workbook(out, "ACIEE Advisor", "1.0")) {
            var sheet = workbook.newWorksheet(table.getTitle());
            for (int c = 0; c < table.getColumns().size(); c++) {
                sheet.value(0, c, table.getColumns().get(c));
                sheet.style(0, c).bold().wrapText(true).set();
            }
            for (int r = 0; r < table.getRows().size(); r++) {
                var row = table.getRows().get(r);
                for (int c = 0; c < row.size(); c++) {
                    switch (row.get(c)) {
                        case Number number -> sheet.value(r + 1, c, number);
                        case String text -> sheet.value(r + 1, c, text);
                        case null, default -> { }
                    }
                }
            }
            sheet.freezePane(1, 1);
        }
    }

    private void pdf(ReportTable table, OutputStream out) throws IOException {
        var regular = new Font(BaseFont.createFont("liberation/LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED), 8);
        var bold = new Font(BaseFont.createFont("liberation/LiberationSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED), 8);
        var heading = new Font(bold.getBaseFont(), 14);
        var document = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(table.getTitle(), heading));
        document.add(new Paragraph("Generat la %s. Mediile apar doar pentru perechile cu cel puțin k evaluări; „–” înseamnă blocat sau fără note."
                .formatted(LocalDate.now()), regular));
        if (table.getSections().isEmpty()) {
            document.add(pdfTable(table, IntStream.range(0, table.getColumns().size()).boxed().toList(), regular, bold));
        }
        for (var section : table.getSections()) {
            var columns = IntStream.concat(IntStream.range(0, table.getKeyColumns()),
                    IntStream.range(section.getFromColumn(), section.getToColumn())).boxed().toList();
            document.add(new Paragraph(section.getTitle(), new Font(bold.getBaseFont(), 11)));
            document.add(pdfTable(table, columns, regular, bold));
        }
        document.close();
    }

    private PdfPTable pdfTable(ReportTable table, List<Integer> columns, Font regular, Font bold) {
        var pdfTable = new PdfPTable(columns.size());
        pdfTable.setWidthPercentage(100);
        pdfTable.setSpacingBefore(6);
        pdfTable.setHeaderRows(1);
        var widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int column = columns.get(i);
            int longest = table.getRows().stream().mapToInt(row -> String.valueOf(row.get(column)).length()).max().orElse(0);
            widths[i] = Math.clamp(Math.max(longest, table.getColumns().get(column).length() / 3), 3, 30);
            pdfTable.addCell(new Phrase(table.getColumns().get(column), bold));
        }
        pdfTable.setWidths(widths);
        for (var row : table.getRows()) {
            for (int column : columns) {
                pdfTable.addCell(new Phrase(row.get(column) == null ? "–" : row.get(column).toString(), regular));
            }
        }
        return pdfTable;
    }
}
