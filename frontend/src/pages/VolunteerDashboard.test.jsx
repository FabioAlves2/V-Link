import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import VolunteerDashboard from "./VolunteerDashboard";
import { getVolunteerDashboard } from "../api/user";

vi.mock("../api/user", () => ({ getVolunteerDashboard: vi.fn() }));

function renderPage() {
    return render(
        <MemoryRouter>
            <VolunteerDashboard />
        </MemoryRouter>
    );
}

function cardFor(title) {
    return screen.getByText(title).closest(".MuiCard-root");
}

const sampleSummary = {
    upcomingEvents: [
        { id: 1, title: "Praia Limpa", location: "Matosinhos", startDate: "2027-01-01T10:00:00" },
    ],
    pastEvents: [
        { event: { id: 2, title: "Doação de Sangue", location: "Porto", startDate: "2025-01-01T10:00:00" }, checkedIn: true },
        { event: { id: 3, title: "Limpeza do Rio", location: "Braga", startDate: "2025-02-01T10:00:00" }, checkedIn: false },
    ],
    totalHours: 4.5,
};

describe("VolunteerDashboard", () => {
    beforeEach(() => {
        getVolunteerDashboard.mockReset();
        getVolunteerDashboard.mockResolvedValue({ data: sampleSummary });
    });

    it("renders upcoming/past counts and total hours from the summary", async () => {
        renderPage();

        expect(await screen.findByText("Praia Limpa")).toBeInTheDocument();
        expect(screen.getByText("1")).toBeInTheDocument(); // próximos eventos
        expect(screen.getByText("2")).toBeInTheDocument(); // eventos passados
        expect(screen.getByText("4.5")).toBeInTheDocument(); // horas voluntariadas
    });

    it("shows distinct chips for a checked-in past event and a missed one", async () => {
        renderPage();

        const checkedInCard = await screen.findByText("Doação de Sangue").then(() => cardFor("Doação de Sangue"));
        expect(within(checkedInCard).getByText("Participaste ✓")).toBeInTheDocument();

        const missedCard = cardFor("Limpeza do Rio");
        expect(within(missedCard).getByText("Sem confirmação de presença")).toBeInTheDocument();
    });

    it("shows the empty-state CTA to /events when there are no subscriptions at all", async () => {
        getVolunteerDashboard.mockResolvedValue({ data: { upcomingEvents: [], pastEvents: [], totalHours: 0 } });
        renderPage();

        expect(await screen.findByText("Ainda não te inscreveste em nenhum evento")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /ver eventos/i })).toBeInTheDocument();
    });

    it("shows an error alert when the summary request fails", async () => {
        getVolunteerDashboard.mockRejectedValue({});
        renderPage();

        expect(await screen.findByText("Erro ao carregar o painel.")).toBeInTheDocument();
    });
});
