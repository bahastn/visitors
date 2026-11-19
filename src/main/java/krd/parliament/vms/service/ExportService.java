package krd.parliament.vms.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontSelector;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.util.Dates;
import krd.parliament.vms.util.Labels;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exports reports to Excel (.xlsx) and PDF for official records. */
@Service
public class ExportService {

    private final Labels labels;
    private final Dates dates;
    private final SettingsService settings;

    public ExportService(Labels labels, Dates dates, SettingsService settings) {
        this.labels = labels;
        this.dates = dates;
        this.settings = settings;
    }

    private boolean rtl() {
        String l = LocaleContextHolder.getLocale().getLanguage();
        return "ar".equals(l) || "ckb".equals(l);
    }

    private List<String> requestHeaders() {
        return List.of(m("req.reference"), m("office.office"), m("visitor.name"), m("visitor.phone"), m("visitor.email"),
                m("visitor.organization"), m("visitor.governorate"), m("req.purpose"), m("req.subject"),
                m("req.priority"), m("req.status"), m("req.channel"), m("req.managedBy"), m("req.created"),
                m("req.scheduled"), m("req.checkIn"), m("req.meetingMinutes"));
    }

    private List<String> requestRow(VisitRequest r) {
        List<String> row = new ArrayList<>();
        row.add(r.getReferenceNumber());
        row.add(r.getOffice().getName());
        row.add(r.getVisitor().getFullName());
        row.add(r.getVisitor().getPhone());
        row.add(nz(r.getVisitor().getEmail()));
        row.add(nz(r.getVisitor().getOrganization()));
        row.add(labels.t(r.getVisitor().getGovernorate()));
        row.add(labels.t(r.getPurpose()));
        row.add(r.getSubject());
        row.add(labels.t(r.getPriority()));
        row.add(labels.t(r.getStatus()));
        row.add(labels.t(r.getChannel()));
        row.add(r.getManagedBy() == null ? "" : r.getManagedBy().getFullName());
        row.add(dates.dt(r.getCreatedAt()));
        row.add(dates.dt(r.getScheduledStart()));
        row.add(dates.dt(r.getCheckInAt()));
        row.add(r.getActualMeetingMinutes() == null ? "" : String.valueOf(r.getActualMeetingMinutes()));
        return row;
    }

    private List<String[]> summaryRows(ReportService.Report rep) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{m("report.period"), rep.from + " → " + rep.to});
        rows.add(new String[]{m("report.total"), String.valueOf(rep.total)});
        rows.add(new String[]{m("report.completed"), String.valueOf(rep.completed)});
        rows.add(new String[]{m("report.pending"), String.valueOf(rep.pending)});
        rows.add(new String[]{m("report.approvalRate"), rep.approvalRate + "%"});
        rows.add(new String[]{m("report.rejectionRate"), rep.rejectionRate + "%"});
        rows.add(new String[]{m("report.noShowRate"), rep.noShowRate + "%"});
        rows.add(new String[]{m("report.avgWait"), rep.avgWaitDays + " " + m("common.days")});
        rows.add(new String[]{m("report.avgLobby"), rep.avgLobbyMinutes + " " + m("common.minutes")});
        rows.add(new String[]{m("report.avgMeeting"), rep.avgMeetingMinutes + " " + m("common.minutes")});
        rows.add(new String[]{m("report.delegations"), String.valueOf(rep.delegationVisits)});
        rows.add(new String[]{m("report.attendees"), String.valueOf(rep.totalAttendees)});
        rows.add(new String[]{m("report.avgRating"), rep.avgRating + " / 5 (" + rep.feedbackCount + ")"});
        return rows;
    }

    // ------------------------------------------------------------------ Excel

    public byte[] excel(ReportService.Report rep, String scopeTitle) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet s = wb.createSheet(safe(m("report.summary")));
            s.setRightToLeft(rtl());
            Row title = s.createRow(0);
            title.createCell(0).setCellValue(settings.orgName() + " — " + scopeTitle);
            int i = 2;
            for (String[] r : summaryRows(rep)) {
                Row row = s.createRow(i++);
                row.createCell(0).setCellValue(r[0]);
                row.createCell(1).setCellValue(r[1]);
            }
            s.autoSizeColumn(0);
            s.autoSizeColumn(1);

            Sheet rs = wb.createSheet(safe(m("nav.requests")));
            rs.setRightToLeft(rtl());
            List<String> h = requestHeaders();
            Row hr = rs.createRow(0);
            for (int c = 0; c < h.size(); c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(h.get(c));
                cell.setCellStyle(head);
            }
            int rIdx = 1;
            for (VisitRequest r : rep.requests) {
                Row row = rs.createRow(rIdx++);
                List<String> vals = requestRow(r);
                for (int c = 0; c < vals.size(); c++) {
                    row.createCell(c).setCellValue(vals.get(c));
                }
            }
            for (int c = 0; c < h.size(); c++) {
                rs.autoSizeColumn(c);
            }
            rs.createFreezePane(0, 1);

            breakdownSheet(wb, head, m("report.byPurpose"), translate(rep.byPurpose));
            breakdownSheet(wb, head, m("report.byGovernorate"), translate(rep.byGovernorate));
            breakdownSheet(wb, head, m("report.byOrgType"), translate(rep.byOrgType));
            breakdownSheet(wb, head, m("report.byStatus"), translate(rep.byStatus));
            breakdownSheet(wb, head, m("report.byChannel"), translate(rep.byChannel));
            breakdownSheet(wb, head, m("report.perDay"), rep.visitsPerDay);
            breakdownSheet(wb, head, m("report.perWeek"), rep.visitsPerWeek);
            breakdownSheet(wb, head, m("report.perMonth"), rep.visitsPerMonth);
            breakdownSheet(wb, head, m("report.topOrgs"), rep.topOrganizations);
            breakdownSheet(wb, head, m("report.byManager"), rep.byManager);
            breakdownSheet(wb, head, m("report.byOffice"), rep.byOffice);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void breakdownSheet(Workbook wb, CellStyle head, String name, Map<String, Long> data) {
        Sheet s = wb.createSheet(safe(name));
        s.setRightToLeft(rtl());
        Row h = s.createRow(0);
        Cell a = h.createCell(0);
        a.setCellValue(name);
        a.setCellStyle(head);
        Cell b = h.createCell(1);
        b.setCellValue(m("common.count"));
        b.setCellStyle(head);
        int i = 1;
        for (var e : data.entrySet()) {
            Row r = s.createRow(i++);
            r.createCell(0).setCellValue(e.getKey());
            r.createCell(1).setCellValue(e.getValue());
        }
        s.autoSizeColumn(0);
    }

    // ------------------------------------------------------------------ PDF

    public byte[] pdf(ReportService.Report rep, String scopeTitle) throws IOException {
        try {
            return buildPdf(rep, scopeTitle);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("PDF export failed: " + e.getMessage(), e);
        }
    }

    private byte[] buildPdf(ReportService.Report rep, String scopeTitle) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        FontSelector normal = selector(10, false);
        FontSelector boldSel = selector(10, true);
        FontSelector titleSel = selector(15, true);
        int dir = rtl() ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;

        PdfPTable title = new PdfPTable(1);
        title.setWidthPercentage(100);
        title.setRunDirection(dir);
        title.addCell(cell(titleSel.process(settings.orgName() + " — " + scopeTitle), null, dir));
        title.addCell(cell(boldSel.process(m("report.title") + "  " + rep.from + " → " + rep.to), null, dir));
        title.addCell(cell(normal.process(m("report.generated") + ": " + dates.dt(java.time.LocalDateTime.now())), null, dir));
        doc.add(title);
        doc.add(new Paragraph(" "));

        PdfPTable sum = new PdfPTable(new float[]{3, 2});
        sum.setWidthPercentage(55);
        sum.setHorizontalAlignment(rtl() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        sum.setRunDirection(dir);
        for (String[] r : summaryRows(rep)) {
            sum.addCell(cell(boldSel.process(r[0]), new Color(232, 245, 233), dir));
            sum.addCell(cell(normal.process(r[1]), null, dir));
        }
        doc.add(sum);
        doc.add(new Paragraph(" "));

        PdfPTable breakdowns = new PdfPTable(3);
        breakdownBlock(breakdowns, m("report.byPurpose"), translate(rep.byPurpose), boldSel, normal, dir);
        breakdownBlock(breakdowns, m("report.byGovernorate"), translate(rep.byGovernorate), boldSel, normal, dir);
        breakdownBlock(breakdowns, m("report.byOrgType"), translate(rep.byOrgType), boldSel, normal, dir);
        breakdowns.setWidthPercentage(100);
        breakdowns.setRunDirection(dir);
        doc.add(breakdowns);

        doc.newPage();
        List<String> h = requestHeaders();
        int[] cols = {0, 1, 2, 3, 6, 8, 10, 14};
        PdfPTable t = new PdfPTable(cols.length);
        t.setWidthPercentage(100);
        t.setRunDirection(dir);
        t.setHeaderRows(1);
        t.setWidths(new float[]{2.4f, 2.6f, 3.2f, 2.2f, 2f, 3.8f, 1.8f, 2.2f});
        FontSelector small = selector(8, false);
        FontSelector smallBold = selector(8, true);
        for (int c : cols) {
            t.addCell(cell(smallBold.process(h.get(c)), new Color(200, 230, 201), dir));
        }
        for (VisitRequest r : rep.requests) {
            List<String> row = requestRow(r);
            for (int c : cols) {
                t.addCell(cell(small.process(row.get(c)), null, dir));
            }
        }
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    private void breakdownBlock(PdfPTable outer, String title, Map<String, Long> data, FontSelector bold, FontSelector normal, int dir)
            throws Exception {
        PdfPTable inner = new PdfPTable(new float[]{3, 1});
        inner.setRunDirection(dir);
        PdfPCell head = cell(bold.process(title), new Color(200, 230, 201), dir);
        head.setColspan(2);
        inner.addCell(head);
        for (var e : data.entrySet()) {
            inner.addCell(cell(normal.process(e.getKey()), null, dir));
            inner.addCell(cell(normal.process(String.valueOf(e.getValue())), null, dir));
        }
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBorder(Rectangle.NO_BORDER);
        wrap.setPadding(4);
        outer.addCell(wrap);
    }

    private PdfPCell cell(Phrase p, Color bg, int dir) {
        PdfPCell c = new PdfPCell(p);
        c.setRunDirection(dir);
        c.setPadding(4);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    private FontSelector selector(float size, boolean bold) throws IOException {
        FontSelector fs = new FontSelector();
        fs.addFont(new com.lowagie.text.Font(font(bold ? "fonts/NotoSans_700Bold.ttf" : "fonts/NotoSans_400Regular.ttf"), size));
        fs.addFont(new com.lowagie.text.Font(font(bold ? "fonts/NotoSansArabic_700Bold.ttf" : "fonts/NotoSansArabic_400Regular.ttf"), size));
        return fs;
    }

    private BaseFont font(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private <K extends Enum<?>> Map<String, Long> translate(Map<K, Long> m) {
        Map<String, Long> res = new java.util.LinkedHashMap<>();
        m.forEach((k, v) -> res.put(labels.t(k), v));
        return res;
    }

    private String m(String key) {
        return labels.msg(key);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String sheetName) {
        String s = sheetName.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
