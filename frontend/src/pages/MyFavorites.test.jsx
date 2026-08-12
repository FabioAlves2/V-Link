import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import MyFavorites from "./MyFavorites";
import { getFavorites, unfavoriteEvent } from "../api/user";

vi.mock("../api/user", () => ({
    getFavorites: vi.fn(),
    unfavoriteEvent: vi.fn(),
}));

const sampleEvents = [
    { id: 1, title: "Praia Limpa", location: "Matosinhos", startDate: "2027-01-01T10:00:00" },
    { id: 2, title: "Doação de Sangue", location: "Porto", startDate: "2027-02-01T10:00:00" },
];

function renderPage() {
    return render(
        <MemoryRouter>
            <MyFavorites />
        </MemoryRouter>
    );
}

function cardFor(title) {
    return screen.getByText(title).closest(".MuiCard-root");
}

describe("MyFavorites", () => {
    beforeEach(() => {
        getFavorites.mockReset();
        unfavoriteEvent.mockReset();
        getFavorites.mockResolvedValue({ data: sampleEvents });
    });

    it("renders fetched favorites", async () => {
        renderPage();

        expect(await screen.findByText("Praia Limpa")).toBeInTheDocument();
        expect(screen.getByText("Doação de Sangue")).toBeInTheDocument();
    });

    it("removes a favorite on click without navigating", async () => {
        unfavoriteEvent.mockResolvedValue({});
        const user = userEvent.setup();
        renderPage();

        const card = await screen.findByText("Praia Limpa").then(() => cardFor("Praia Limpa"));
        await user.click(within(card).getByRole("button", { name: /remover dos favoritos/i }));

        await waitFor(() => expect(unfavoriteEvent).toHaveBeenCalledWith(1));
        expect(screen.queryByText("Praia Limpa")).not.toBeInTheDocument();
    });

    it("shows the empty-state card with a CTA to /events when there are no favorites", async () => {
        getFavorites.mockResolvedValue({ data: [] });
        renderPage();

        expect(await screen.findByText("Ainda não tens eventos favoritos")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /ver eventos/i })).toBeInTheDocument();
    });

    it("shows an error alert when the favorites request fails", async () => {
        getFavorites.mockRejectedValue({});
        renderPage();

        expect(await screen.findByText("Erro ao carregar favoritos.")).toBeInTheDocument();
    });
});
