// Spreadsheet apps (Excel, Sheets, LibreOffice) treat a cell starting with =, +, -, or @ as a
// formula. Without this, a subscriber registering as e.g. `=HYPERLINK(...)` gets that formula
// executed the moment the promoter opens the exported CSV — classic CSV/formula injection.
const FORMULA_PREFIX_RE = /^[=+\-@\t\r]/;

function escapeCsvValue(value) {
    let str = value == null ? "" : String(value);
    if (FORMULA_PREFIX_RE.test(str)) str = "'" + str;
    return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
}

export function toCsv(rows, headers) {
    const headerLine = headers.map((h) => escapeCsvValue(h.label)).join(",");
    const lines = rows.map((row) =>
        headers.map((h) => escapeCsvValue(row[h.key])).join(",")
    );
    return [headerLine, ...lines].join("\n");
}

export function downloadCsv(filename, csvString) {
    const blob = new Blob([csvString], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}
