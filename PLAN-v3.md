# VMS v3 — UI & Process Simplification: Technical Plan

Nov 19, 2026 · @bahast

## 1. Goal and what is wrong today

Rebuild the UI of the existing VMS (Spring Boot 3.3 + Thymeleaf + PostgreSQL, Kurdish/Arabic/English) as an ERP-style app: Odoo look, ERPNext navigation, fewer words, fewer clicks, fewer statuses. Keep the backend, data model and multi-office logic; change templates, CSS, JS, and simplify the request workflow in `VisitRequestService`.

**Do not** rewrite in another framework, add a SPA, or change the database except where section 5 says so.

Problems seen in the current screens:

| Area | Problem | Fix direction |
| --- | --- | --- |
| Navigation | 20+ links in a dark sidebar in 5 groups; hard to scan; sidebar disappears on some pages (Waiting list screenshot) | Top navbar with 6 apps + app home grid (section 3) |
| Wording | Every page has a subtitle, hints, helper paragraphs (calendar side panel, ready-to-schedule text, greeting + 3 calendars in the date line) | One title per page, no subtitles, no help paragraphs; tooltips only |
| Duplicate actions | "New request" appears in top bar AND page header | One primary button per page, in the control panel |
| Two lists for one thing | Waiting list and All requests show the same records with different filters | One Visits app with List / Kanban / Calendar views and saved filters |
| Calendar | Main calendar renders empty; mini month is empty; legend and toggles take space | Fix rendering; calendar becomes a view of Visits (section 6) |
| Dashboard | Day ribbon is empty most days; 6 KPI tiles; 5 panels; looks busy | 4 KPIs, 2 charts, 2 short lists, Odoo-style (section 6) |
| Process | 11 statuses, recommendation step, two-level approval, referral and transfer all visible to everyone | 5 main stages + 2 end states; advanced steps hidden behind settings (section 5) |
| Forms | Long single page forms with many optional fields | Quick-create modal (4 fields) + full form only when needed |
| Numbers and dates | Mixed Arabic-Indic and Latin digits ("١٦ ڕۆژ" next to "2026-11-13"); browser `mm/dd/yyyy` inputs in English | One digit style, one date format, localized date picker |

## 2. Design system (Odoo-style)

Light, flat, white surfaces on a light grey canvas, one strong brand colour, one accent. Put every value below in one file, `static/css/theme.css`, as Bootstrap 5.3 CSS variables (`--bs-primary`, `--bs-body-bg`, etc.) so Bootstrap components pick them up. Delete the current `app.css` and rebuild it from these tokens; target under 600 lines.

**Colours**

| Token | Value | Use |
| --- | --- | --- |
| `--brand` | `#714B67` (Odoo purple) | Navbar, primary buttons, active tab, links |
| `--brand-dark` | `#5A3A52` | Hover / pressed |
| `--accent` | `#017E84` (Odoo teal) | Secondary actions, selected filter chips, calendar "today" |
| `--canvas` | `#F5F5F7` | Page background |
| `--surface` | `#FFFFFF` | Cards, tables, forms |
| `--border` | `#E2E4E8` | All 1px borders |
| `--text` | `#1F2328` | Body text |
| `--muted` | `#6B7280` | Secondary text, column headers |
| `--success` / `--warning` / `--danger` / `--info` | `#28A745` / `#F0AD4E` / `#D9534F` / `#17A2B8` | Status badges only |

The parliament palette from v2 (`#003049 #f77f00 #9e2a2b #001d3d #05668d #373d20 #f68e5f`) is kept as the **office colour list** (each office gets one as its dot / calendar colour) and as the chart series colours. Optional setting: Admin can switch `--brand` to `#003049` and `--accent` to `#f77f00` for a parliament theme; implement as a `data-theme="parliament"` attribute on `<html>` that overrides 4 variables.

**Type and spacing**

- Font: Vazirmatn for all three languages (already bundled), sizes 13px body, 12px table/meta, 18px page title, 22px KPI number. Weights 400 / 500 / 600 only.
- Digits: Latin digits (0-9) everywhere, in all languages, for references, dates, times and counts. Remove Arabic-Indic digit output.
- Spacing scale 4 / 8 / 12 / 16 / 24 px. Border radius 4px on inputs and buttons, 6px on cards. No shadows except dropdowns and modals (`0 4px 12px rgba(0,0,0,.08)`). No gradients.
- Density: table rows 40px, inputs 32px (`form-control-sm` style as the default), buttons 32px.

**Components (Bootstrap 5.3 only, no extra UI kit)**

- Buttons: `btn-primary` (brand, one per screen), `btn-secondary` (white, bordered), `btn-link`. No outline-coloured buttons.
- Status badge: pill, 11px, tinted background + coloured text, one colour per stage (section 5).
- Office tag: 8px coloured dot + office short name (code), never the full long name in tables.
- Avatars: initials circle, 24px in lists, 32px in navbar.
- Icons: Bootstrap Icons, 16px, always paired with a label in menus; icon-only buttons need `title` + `aria-label`.
- Empty state: one icon, one short line, one button. No paragraphs.

## 3. Navigation

Replace the dark sidebar with an Odoo-style top navbar and an ERPNext-style app home. Every screen is at most 2 clicks from anywhere.

**3.1 Top navbar (one row, 48px, brand colour, every page)**

From the start side to the end side (mirrors automatically in RTL):

1. App-grid icon (`bi-grid-3x3-gap`) → opens the App home.
2. Current app name (bold, e.g. "Visits").
3. The current app's menus as plain text links (max 5, listed in 3.3). On screens under 992px they collapse into a "☰" dropdown.
4. Spacer.
5. Awesome bar: search input 280px, shortcut `/` or `Ctrl+K` (see 3.4).
6. Office switcher: compact dropdown showing the office code + colour dot ("DSP ●"), with "All my offices" at the top. Hidden for users with one office.
7. Bell with unread count; opens a dropdown of the last 8 notifications + "See all".
8. Language switcher: 3-letter dropdown (KU / AR / EN).
9. Avatar dropdown: Profile, Two-factor login, Settings (admins), Sign out.

Remove: the sidebar, the sidebar office box, the top-bar "New request" button, the translate icon, the user name/role text next to the avatar.

**3.2 App home (`/` after login, and the grid icon)**

A centered grid of large app tiles (Odoo home): 64px rounded square icon in the app's colour + name underneath; badge with a count where useful. Under the grid, one row of "Today" numbers (Expected, In lobby, Waiting decision, Overdue), each clickable into the filtered list. Users see only the apps their role allows. A role's default app can be set (Reception → Front Desk opens directly after login).

**3.3 The six apps and their top-bar menus**

| App (tile) | Icon | Who sees it | Top-bar menus inside the app |
| --- | --- | --- | --- |
| Dashboard | `bi-speedometer2` | Head, Director, Secretary, Admin, Auditor | (none) |
| Visits | `bi-calendar2-check` | Head, Director, Secretary, Admin, Auditor | Visits · To decide (count) · Calendar · Cases · Tasks |
| Front Desk | `bi-qr-code-scan` | Reception, Security, Admin | Today · Scan · Lobby queue · Watchlist (Security) · Lobby screen |
| Visitors | `bi-people` | all staff roles | Visitors · Delegations · Watchlist |
| Reports | `bi-bar-chart` | Head, Director, Admin, Auditor | Overview · By office · Feedback · Activity log |
| Settings | `bi-gear` | Admin | Offices · Users · General · Messages (SMS/WhatsApp log) · Backups |

The old pages map into these apps: Waiting list + All requests + Timeline → Visits views; Reception + Security desk + Queue → Front Desk; Mobile page → removed (the new layout is responsive; see 6.8).

**3.4 Awesome bar (ERPNext-style)**

One input that does three things, shown as grouped results in a dropdown while typing (debounce 250ms, endpoint `GET /api/search?q=` returning JSON, max 5 per group):

- **Go to**: typing a reference (`DSP-2026-000123`) or phone number jumps straight to the record on Enter.
- **Records**: Visitors, Visits, Cases matching name / phone / subject.
- **Actions**: "New visit", "New visitor", "Open calendar", "Scan pass" — matched by words in all 3 languages.

Keyboard: ↑ ↓ to move, Enter to open, Esc to close.

**3.5 Breadcrumbs**

Every page under an app shows a breadcrumb in the control panel (section 4): `Visits / DSP-2026-000123`. Clicking the first part returns to the list with the same filters (keep filters in the URL query string).

## 4. Standard page patterns

Every screen is built from four reusable Thymeleaf fragments, so all apps look and behave the same. No page may invent its own header or layout.

&#91;embedded content: record screen wireframe · navbar, control panel, status bar, sheet + chatter\]

In RTL the whole layout mirrors: the app name and buttons sit on the right, search and the stage ribbon on the left.

**4.1 Control panel (fragment `cp`)** — a white bar under the navbar, 2 rows, full width:

- Row 1, start side: breadcrumb. End side: search box with filter chips inside it (Odoo style: typing shows "Search Visitor for: … / Subject for: …"; chosen filters appear as removable chips in the box).
- Row 2, start side: the one primary button ("New") + secondary buttons. Middle: "Filters ▾" and "Group by ▾" dropdowns. End side: pager ("1-20 / 128 ‹ ›") + view switcher icons (List · Kanban · Calendar), only the views the model has.
- Filters dropdown holds predefined filters (My visits, To decide, Today, This week, Overdue, VIP) + a date-range picker. Group by: Status, Office, Purpose, Managed by. Remove the current 7-field filter form.

**4.2 List view (fragment `list`)**

- Plain table, white, 40px rows, checkbox column, sortable headers, no row tinting except overdue (light red) and selected (light brand).
- Max 6 columns. Visits list: Reference · Visitor (name + small org line) · Office (dot + code) · Date · Managed by (avatar) · Status badge. Nothing else.
- Row click opens the form. Selecting rows replaces the control-panel buttons with an "Actions ▾" dropdown (Approve, Reject, Assign, Export) + "3 selected".
- Group by renders collapsible group header rows with counts.

**4.3 Kanban view (fragment `kanban`)**

- One column per stage (section 5), column header = stage name + count, horizontally scrollable.
- Card: visitor name (bold), subject (1 line, ellipsis), office dot, date, priority star, manager avatar. 4 lines max.
- Drag a card to another column = run that transition. If the transition needs input (reject reason, schedule time), open a small modal; on cancel the card goes back.
- Columns "Rejected" and "Cancelled" are folded (narrow, vertical title) by default.

**4.4 Form view (fragment `form`)** — the record page, ERPNext/Odoo style:

- Under the control panel: a **status bar**. Start side: action buttons for the current stage only (e.g. at "New": Approve, Reject). End side: the stage ribbon as chevrons (New › Approved › Scheduled › Arrived › Done), current one filled with brand colour. Clicking a later chevron is not allowed; clicking an earlier one is allowed only for Director/Admin.
- "⋯" button holds rare actions: Postpone, Transfer to office, Refer outside, Print badge, Duplicate, Delete.
- Sheet: white card, max 1100px, two columns of label : value fields. Read mode by default; "Edit" turns fields into inputs in place (no separate edit page). Save / Discard appear in the control panel while editing.
- Notebook tabs under the fields: Attendees · Files · Outcome · Tasks. Hide a tab when the stage makes it irrelevant (Outcome only from Arrived).
- **Chatter** (Odoo): a column at the end side on screens ≥1400px, under the sheet on smaller screens. It merges notes, history (audit log), messages sent to the visitor and system events into one timeline, newest first, with a "Log note" box at the top. This replaces the History tab, Notes tab and the separate Timeline section.

**4.5 Quick-create modal (fragment `quick`)**

"New" opens a modal, not a page, with only: Visitor (autocomplete; type a new name + phone to create on the fly) · Office (preselected from the switcher) · Subject · Preferred date. Buttons: "Create" and "Create and edit" (opens the full form). The "pick visitor first" page is removed.

## 5. Simplified visit process

Cut the visit workflow from 11 statuses to 5 stages plus 2 end states. Every stage has at most 2 main buttons; everything else moves to the "⋯" menu or to a field.

&#91;embedded content: visit status flow · 5 stages, 2 end states\]

Approved visits can also be cancelled, and a Director can reopen Rejected or Cancelled visits back to New.

**5.1 New status model** (`VisitStatus` enum)

| New status | Replaces (v2) | Badge colour | Main buttons at this stage | Who |
| --- | --- | --- | --- | --- |
| `NEW` | REQUESTED, UNDER\_REVIEW, POSTPONED | info | Approve · Reject | Head, Director (Secretary sees "Recommend" if the office uses two levels) |
| `APPROVED` | APPROVED | accent | Schedule | Secretary, Director |
| `SCHEDULED` | SCHEDULED | brand | Check in · Cancel | Reception / Secretary |
| `ARRIVED` | CHECKED\_IN, IN\_MEETING | warning | Start meeting / End meeting (one toggle) | Reception, Secretary |
| `DONE` | COMPLETED | success | Add outcome · Create task | Secretary |
| `REJECTED` | REJECTED, REFERRED | danger | Reopen (Director) | — |
| `CANCELLED` | CANCELLED, NO\_SHOW | muted | Reopen (Director) | — |

**5.2 What happens to the removed steps**

- **Under review** → gone. A request is "New" until someone decides. Opening it does not change its status.
- **Postponed** → stays `NEW` with a `followUpDate` field; the "To decide" filter hides it until that date. Button "Postpone" lives in "⋯".
- **Recommendation / two-level approval** → not a stage. If the office setting "Two levels" is on, Secretary gets a "Recommend ▾" button (Approve / Reject + note) which only adds a tag and a chatter entry; Head/Director still press Approve. Default for new offices: off.
- **Referred outside** → `REJECTED` with `closeReason = REFERRED` + the external body; the visitor message stays the "referred" text.
- **Transfer to another office** → keeps `NEW`, changes `office`, writes a chatter line. In "⋯".
- **In meeting** → `ARRIVED` with `meetingStartAt` set; the badge text changes to "In meeting" but the stage is the same.
- **No-show** → `CANCELLED` with `closeReason = NO_SHOW`; the nightly job still sets it automatically.
- **Approve + schedule in one step**: the Approve modal has an optional date/time; if filled, the request goes straight to `SCHEDULED`.

**5.3 Data changes** (the only schema change in this plan)

- Add to `VisitRequest`: `closeReason` (enum `REJECTED, REFERRED, NO_SHOW, CANCELLED_BY_VISITOR, CANCELLED_BY_OFFICE`), `followUpDate` (date).
- One SQL migration file `db/migrate-v3.sql`, run once, documented in the README:

```sql
UPDATE visit_request SET status='NEW' WHERE status IN ('REQUESTED','UNDER_REVIEW');
UPDATE visit_request SET status='NEW', follow_up_date=postponed_until WHERE status='POSTPONED';
UPDATE visit_request SET status='ARRIVED' WHERE status IN ('CHECKED_IN','IN_MEETING');
UPDATE visit_request SET status='DONE' WHERE status='COMPLETED';
UPDATE visit_request SET status='REJECTED', close_reason='REFERRED' WHERE status='REFERRED';
UPDATE visit_request SET status='CANCELLED', close_reason='NO_SHOW' WHERE status='NO_SHOW';
```

- Update `VisitStatus.allowedNext()`, `getStage()`, `pendingStatuses()`, `occupyingCalendar()`, every repository query and every report that names old statuses. Search the code for each old name and leave none.
- Visitor messages (`msg.visitor.*`) and staff notifications stay; map them to the new transitions. Remove notification keys that no longer fire.

## 6. Screen by screen

Each screen below uses only the patterns from section 4. "Remove" means delete the template and its controller method; old URLs return a redirect (301) to the new place.

**6.1 Dashboard** (layout like the reference image: clean cards, charts, no clutter)

- Row 1: 4 KPI cards (number 22px + label 12px + small change vs last week in green/red): Waiting decision · Today's visits · In lobby now · Approval rate (30 days). Each card links to the filtered Visits list.
- Row 2 (2/3 + 1/3): line chart "Requests vs completed visits, last 30 days" with a period dropdown (7d / 30d / 12m) · donut "Requests by purpose" with the total in the centre.
- Row 3 (1/2 + 1/2): "To decide" list (5 rows: visitor, office dot, days waiting, Approve/Reject icon buttons) · "Today's agenda" list (time, visitor, status badge). Each with a "View all" link.
- Remove: greeting line, Hijri/Kurdish date string, day ribbon, recent activity panel, approved-unscheduled panel, tasks panel, morning-summary button (move to Settings › General as "Send now").
- Chart.js (already bundled): thin lines (2px), area fill 8% opacity, no grid lines on x, light grid on y, legend at top end, tooltips on.

**6.2 Visits app — List** (default view): columns and behaviour from 4.2. Default filter by role: Head/Director → "To decide"; Secretary → "My visits"; Auditor → none.

**6.3 Visits app — Kanban**: columns NEW · APPROVED · SCHEDULED · ARRIVED · DONE (+ folded REJECTED, CANCELLED). Dragging NEW→APPROVED opens the Approve modal; APPROVED→SCHEDULED opens the Schedule modal (date, time, duration, room); →REJECTED asks for a reason. Show the last 30 days of DONE only.

**6.4 Visits app — Form**: as 4.4. Field layout, left column: Visitor (link), Organisation, Phone, Office, Managed by. Right column: Subject, Purpose, Priority (stars 0-3 instead of a dropdown), Date & time, Room. Attendees tab: delegation members as an editable inline table (Name, Role, Phone, ID; add row at the bottom).

**6.5 Calendar** (Visits › Calendar, also a view icon in the list)

- FullCalendar full width; no side panel by default. Toolbar: ‹ Today › · title · Day / Week / Month segmented control. Week view is default; time range 07:00-18:00 from the office hours; `firstDay` = Saturday.
- Event block: time + visitor name, coloured by office (all-offices scope) or by status (one office). Blocked time = grey striped.
- Click an event → small popover (visitor, subject, room, status + "Open" and the stage buttons). Drag/resize to reschedule (confirm toast with Undo). Click an empty slot → quick menu: "Schedule an approved request" (searchable list) or "Block this time".
- A collapsible end-side drawer "Unscheduled (n)" lists approved requests as draggable chips; it opens only when n > 0. Remove the legend box, toggles panel, mini month and help paragraph.
- Keep the ICS feed button inside "⋯" ("Sync with Google / Outlook").

**6.6 Front Desk app** (Reception and Security work here all day; optimise for speed)

- "Today" screen: big search/scan input at the top (autofocus; accepts QR scanner input or a typed reference/phone/name). Below it two columns: Expected (scheduled today, sorted by time) and In building (arrived, with wait minutes). Each row has one big button: "Check in" or "Done".
- Check-in opens a side drawer: photo capture (optional), ID number, badge print checkbox, watchlist result shown as a coloured banner. One "Confirm check-in" button.
- Lobby queue and the lobby TV screen keep their function; restyle with the new tokens.
- Merge Security desk into Front Desk: Security users see the same screen plus watchlist alerts at the top and the Watchlist menu.

**6.7 Visitors, Reports, Settings**

- Visitors: list + form with the same patterns; form chatter shows all their visits. Remove the separate visitor "view" vs "edit" pages (edit in place).
- Reports: one page with the control panel's date range + office filter; cards and charts like the dashboard; tables only for "By office" and "Feedback". Export button (▾ PDF / Excel) in the control panel.
- Settings: left vertical tab list (General · Offices · Users · Messages · Security · Backups), content on the right, one Save button. Office and user forms use the form pattern.

**6.8 Responsive and mobile**

- Under 768px: navbar shows grid icon, app name, search icon, bell, avatar. Lists become stacked cards (same fields). Kanban scrolls horizontally one column per screen. Form becomes one column, chatter below.
- Delete `mobile.html` and `/m`; heads use the normal Visits › To decide list on the phone. Keep the PWA manifest.

**6.9 Public portal**

- Keep the 3-step request form but restyle: white card on grey canvas, brand header bar, office choice as a simple searchable dropdown (not tiles) when there are more than 6 offices.
- Track page: reference + phone → a vertical 5-step progress (the new stages) + appointment card + pass button.

## 7. Bugs to fix (seen in the v2 screenshots)

Fix these first, in phase 1. For each, find the root cause in the browser console / server log, fix it, and add the check to the acceptance list.

| # | Screen | Bug | Likely cause / what to check |
| --- | --- | --- | --- |
| 1 | Calendar | Main calendar area is blank; only the toolbar shows | Open the console. Likely a JS error in the inline Thymeleaf script (`/*[[...]]*/` values breaking JS, a missing `th:inline="javascript"`), FullCalendar global bundle not loaded, or the events JSON endpoint returning 403/500. Calendar must render with 0 events too. |
| 2 | Calendar | Mini month in the side panel is empty (only ‹ › arrows) | Same script stops before building it. Mini month is removed in v3 anyway (6.5). |
| 3 | Waiting list | Whole sidebar missing on this page | Page probably rendered with a layout fragment error or a CSS rule hiding `.sidebar` at that width. Disappears with the new navbar; verify every page uses the one layout. |
| 4 | All filters | Date inputs show `mm/dd/yyyy` in English inside a Kurdish page | Native `<input type=date>` follows the browser locale. Replace with a bundled date picker (flatpickr, with `ckb`/`ar`/`en` texts and Latin digits) showing `YYYY-MM-DD`. |
| 5 | Everywhere | Mixed digits: "١٦ ڕۆژ" and "٥ داواکاری" next to "2026-11-13" | Some messages format numbers with the locale (`NumberFormat` / `#numbers`), others don't. Use Latin digits everywhere (2.Type). |
| 6 | Waiting list | Sort dropdown text runs under its chevron icon | RTL select padding: Bootstrap RTL puts the arrow on the left; custom CSS adds padding on the wrong side. Use logical properties (`padding-inline-end`). |
| 7 | All requests | Date cells have a different grey background than the rest of the row; row heights jump | Per-cell background from `.table-hover` + custom `td` styles. One row style only (4.2). |
| 8 | All requests | The same "VIP" tag shows twice on one row | Visitor VIP flag and request priority VIP both render. Show one star/flag. |
| 9 | Dashboard | Day ribbon "now" marker sits at 18:00 outside working hours; ribbon empty | Ribbon removed in v3 (6.1). |
| 10 | Dashboard / lists | Long Hijri + Kurdish + Gregorian date line under the greeting | Removed. Dates shown as `2026-11-19`, time `14:30`, 24h. |
| 11 | Top bar | "New request" button on every page, plus another in the page header | Removed from the top bar (3.1). |

## 8. Technical rules and file changes

**8.1 Stack decisions**

- Keep Spring Boot, Thymeleaf, Bootstrap 5.3 (RTL build for ku/ar), Bootstrap Icons, FullCalendar 6, Chart.js 4, Vazirmatn. Everything stays bundled locally (works offline on the parliament network).
- Add **htmx 1.9** (one 14 KB file in `static/vendor/htmx/`) for partial updates: list paging/filtering, kanban moves, chatter posts, quick-create modal, stage buttons. Server returns Thymeleaf fragments (`return "visits/list :: rows"`). No React/Vue/SPA.
- Add **flatpickr** (bundled) for date and date-time inputs, with ku/ar/en texts.
- Add **SortableJS** (bundled) for kanban drag and drop.
- Remove: `reception.js` offline mode only if it blocks the redesign; otherwise restyle.

**8.2 Template structure** (`src/main/resources/templates/`)

```
layout/base.html        navbar + <main> slot + toasts + modal host
layout/navbar.html      app grid, app menus, search, office, bell, lang, avatar
components/cp.html      control panel (breadcrumb, buttons, search+chips, filters, group by, pager, view switch)
components/list.html    table, group rows, empty state
components/kanban.html  columns + card
components/form.html    status bar, sheet, notebook, chatter
components/quick.html   quick-create modal
components/ui.html      badge, office tag, avatar, kpi card, empty state
home.html               app home grid
dashboard.html
visits/{list,kanban,calendar,form}.html
frontdesk/{today,queue,lobby}.html
visitors/{list,form}.html
reports/index.html
settings/{general,offices,office-form,users,user-form,messages,backups}.html
public/*                restyled
```

- Each app's menu is defined once in Java (`AppMenu` enum or `@Bean` list: key, icon, roles, menus) and rendered by `navbar.html`; no hard-coded menu HTML per page.
- Each list page's columns, filters and group-bys are passed from the controller as a small `ListSpec` object so the `cp`/`list` fragments stay generic.

**8.3 Controllers**

- New `HomeController` (`/` → app home; role default app redirect).
- Merge `WaitingListController` + list parts of `RequestController` + `TimelineController` into `VisitController` (`/visits`, `?view=list|kanban|calendar`, `/visits/{id}`), with htmx endpoints `/visits/{id}/stage/{action}` and `/visits/{id}/chatter`.
- Merge `ReceptionController` + `SecurityController` + queue into `FrontDeskController` (`/front-desk`).
- `SearchController` gets `GET /api/search?q=` returning grouped JSON for the awesome bar.
- Old URLs (`/waiting-list`, `/requests/**`, `/reception`, `/security`, `/queue`, `/timeline`, `/m`) → 301 redirects to the new paths.

**8.4 Writing rules (all three languages)**

- Labels 1-2 words. Buttons are verbs ("Approve", "Schedule", "Check in"). The toast after an action uses the same verb ("Approved").
- No page subtitles, no help paragraphs, no "hint" texts under fields; use a `?` tooltip only where a field is truly unclear.
- Delete unused message keys after the redesign; run the key-coverage script (every `#{...}` key exists in `messages.properties`, `_ckb`, `_ar`; no key exists in one file only).
- All new labels added to all 3 files in the same commit. The person reviewing the Kurdish will check wording; keep Sorani terms consistent with v2 (سەردان, داواکاری, نووسینگە, پەسەندکردن).

**8.5 RTL rules**

- Use CSS logical properties only (`margin-inline-start`, `padding-inline-end`, `inset-inline-start`); no `left`/`right` in new CSS.
- Icons that point a direction (chevrons, arrows, send) get class `flip-rtl`.
- Test every screen in Kurdish (RTL) first, then English.

**8.6 Quality rules**

- Keyboard: every action reachable by Tab; visible focus ring in brand colour; Esc closes modals/drawers; `/` focuses search.
- Contrast ≥ 4.5:1 for text (brand purple on white passes; check badges).
- Page load under 1s on the LAN; list pages page by 20 rows server-side; no N+1 queries on lists (use fetch joins for visitor + office + manager).

## 9. Phases and acceptance checklist

Work in 5 phases; each ends with a build that runs (`mvn spring-boot:run`) and a short demo. Do not start a phase before the previous one passes its checks.

1. **Foundation**: fix bugs 1-11; add `theme.css`, `base.html`, `navbar.html`, app home, awesome bar; move every existing page into the new layout unchanged inside.
2. **Patterns**: build `cp`, `list`, `kanban`, `form`, `quick` fragments + htmx, flatpickr, SortableJS; apply them to Visits (list, kanban, form, quick create).
3. **Process**: new status model, migration SQL, service and query updates, stage buttons, chatter, remove old pages and add redirects.
4. **Other apps**: Calendar, Dashboard, Front Desk, Visitors, Reports, Settings, Public portal, responsive layouts.
5. **Polish**: translation pass in all 3 languages, delete unused keys/CSS/templates, README update (new navigation, statuses, migration step, screenshots).

**Acceptance checklist** (test in Kurdish RTL and English, Chrome and Edge, 1366px and 390px wide)

- [x] Every page shows the same navbar; no sidebar anywhere; no page subtitle or help paragraph.
- [x] From any page, any app is 2 clicks away (grid → app); the awesome bar finds a visit by reference, phone or name and opens it with Enter.
- [x] Calendar renders on an empty database and with demo data; drag-to-reschedule and drop-from-unscheduled work and show an Undo toast.
- [x] A Secretary creates a visit in under 20 seconds with the quick-create modal (4 fields).
- [x] A Head approves from the list, the kanban, the form and the dashboard in one click each (two if approving with a time).
- [x] Only these statuses exist in code and data: NEW, APPROVED, SCHEDULED, ARRIVED, DONE, REJECTED, CANCELLED. A search for the old names returns nothing.
- [x] Front Desk: scan a pass → visitor found → checked in, in 3 actions or fewer.
- [x] No English text on a Kurdish or Arabic page (except names and references); Latin digits everywhere; dates `YYYY-MM-DD`, times 24h.
- [x] Key-coverage script reports 0 missing keys in all 3 files.
- [x] Lists of 1,000 visits page in under 1 second; no console errors on any page.
- [x] Old bookmarks (`/waiting-list`, `/requests/5`, `/reception`) redirect to the new pages.
- [x] Database upgrade from v2 works by running `db/migrate-v3.sql` once, with no data loss.
