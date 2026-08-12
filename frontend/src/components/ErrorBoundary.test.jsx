import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import ErrorBoundary from "./ErrorBoundary";

function Thrower() {
    throw new Error("boom");
}

describe("ErrorBoundary", () => {
    beforeEach(() => {
        vi.spyOn(console, "error").mockImplementation(() => {});
    });

    afterEach(() => {
        console.error.mockRestore();
    });

    it("renders children normally when nothing throws", () => {
        render(
            <ErrorBoundary>
                <div>Conteúdo normal</div>
            </ErrorBoundary>
        );

        expect(screen.getByText("Conteúdo normal")).toBeInTheDocument();
    });

    it("shows a fallback instead of a blank screen when a child throws", () => {
        render(
            <ErrorBoundary>
                <Thrower />
            </ErrorBoundary>
        );

        expect(screen.getByText("Ocorreu um erro inesperado.")).toBeInTheDocument();
        expect(screen.queryByText("Conteúdo normal")).not.toBeInTheDocument();
    });
});
