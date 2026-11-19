package krd.parliament.vms.web;

import krd.parliament.vms.domain.Office;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.ExportService;
import krd.parliament.vms.service.ReportService;
import krd.parliament.vms.util.Labels;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ReportController {

    private final ReportService reports;
    private final ExportService export;
    private final Labels labels;
    private final OfficeScope scope;

    public ReportController(ReportService reports, ExportService export, Labels labels, OfficeScope scope) {
        this.reports = reports;
        this.export = export;
        this.labels = labels;
        this.scope = scope;
    }

    private String scopeTitle() {
        Office o = scope.current();
        return o != null ? o.getName() : labels.msg("scope.allOffices");
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) String range, Model model) {
        LocalDate[] r = range(from, to, range);
        ReportService.Report rep = reports.build(r[0], r[1], scope.ids());
        model.addAttribute("rep", rep);
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("status", labels.translateMap(rep.byStatus));
        charts.put("purpose", labels.translateMap(rep.byPurpose));
        charts.put("governorate", labels.translateMap(rep.byGovernorate));
        charts.put("orgType", labels.translateMap(rep.byOrgType));
        charts.put("channel", labels.translateMap(rep.byChannel));
        charts.put("priority", labels.translateMap(rep.byPriority));
        charts.put("office", rep.byOffice);
        Map<String, String> officeColors = new LinkedHashMap<>();
        rep.officeRows.forEach(row -> officeColors.put(row.office().getName(), row.office().getColor()));
        charts.put("officeColors", officeColors);
        model.addAttribute("charts", charts);
        model.addAttribute("from", r[0]);
        model.addAttribute("to", r[1]);
        model.addAttribute("range", range);
        model.addAttribute("scopeTitle", scopeTitle());
        return "reports";
    }

    @GetMapping("/reports/export.xlsx")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        byte[] data = export.excel(reports.build(from, to, scope.ids()), scopeTitle());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=visitor-report-" + from + "_" + to + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/reports/export.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        byte[] data = export.pdf(reports.build(from, to, scope.ids()), scopeTitle());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=visitor-report-" + from + "_" + to + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    static LocalDate[] range(LocalDate from, LocalDate to, String range) {
        LocalDate today = LocalDate.now();
        if (range != null) {
            switch (range) {
                case "today" -> { return new LocalDate[]{today, today}; }
                case "week" -> { return new LocalDate[]{today.minusDays(6), today}; }
                case "month" -> { return new LocalDate[]{today.withDayOfMonth(1), today}; }
                case "lastMonth" -> {
                    LocalDate s = today.minusMonths(1).withDayOfMonth(1);
                    return new LocalDate[]{s, s.plusMonths(1).minusDays(1)};
                }
                case "year" -> { return new LocalDate[]{today.withDayOfYear(1), today}; }
                default -> { }
            }
        }
        LocalDate f = from == null ? today.minusDays(29) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f)) {
            LocalDate x = f;
            f = t;
            t = x;
        }
        return new LocalDate[]{f, t};
    }
}
