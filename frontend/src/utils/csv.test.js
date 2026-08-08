import { describe, it, expect } from "vitest";
import { toCsv } from "./csv";

const HEADERS = [
    { key: "name", label: "Nome" },
    { key: "email", label: "Email" },
];

describe("toCsv", () => {
    it("builds a header row followed by one row per item", () => {
        const rows = [
            { name: "Ana", email: "ana@demo.pt" },
            { name: "João", email: "joao@demo.pt" },
        ];
        expect(toCsv(rows, HEADERS)).toBe(
            "Nome,Email\nAna,ana@demo.pt\nJoão,joao@demo.pt"
        );
    });

    it("quotes values containing commas, quotes or newlines", () => {
        const rows = [{ name: 'Ana, "a" e Beatriz', email: "ana@demo.pt" }];
        expect(toCsv(rows, HEADERS)).toBe(
            'Nome,Email\n"Ana, ""a"" e Beatriz",ana@demo.pt'
        );
    });

    it("renders missing values as an empty cell", () => {
        const rows = [{ name: "Ana", email: null }];
        expect(toCsv(rows, HEADERS)).toBe("Nome,Email\nAna,");
    });

    it("neutralizes leading formula characters to prevent CSV/formula injection", () => {
        const rows = [
            { name: "=cmd", email: "a@demo.pt" },
            { name: "+1+1", email: "b@demo.pt" },
            { name: "-1-1", email: "c@demo.pt" },
            { name: "@SUM(A1:A2)", email: "d@demo.pt" },
        ];
        expect(toCsv(rows, HEADERS)).toBe(
            "Nome,Email\n'=cmd,a@demo.pt\n'+1+1,b@demo.pt\n'-1-1,c@demo.pt\n'@SUM(A1:A2),d@demo.pt"
        );
    });

    it("still applies quote-wrapping on top of the formula prefix when the value also needs it", () => {
        const rows = [{ name: '=HYPERLINK("http://evil","x")', email: "a@demo.pt" }];
        expect(toCsv(rows, HEADERS)).toBe(
            'Nome,Email\n"\'=HYPERLINK(""http://evil"",""x"")",a@demo.pt'
        );
    });

    it("does not alter values that merely contain (not start with) formula characters", () => {
        const rows = [{ name: "Jo=Ana", email: "a+b@demo.pt" }];
        expect(toCsv(rows, HEADERS)).toBe("Nome,Email\nJo=Ana,a+b@demo.pt");
    });
});
