import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import OrganizerDashboard from "./OrganizerDashboard";
import { getMyEvents, updateEvent, deleteEvent } from "../api/event";

vi.mock("../api/event", () => ({
    getMyEvents: vi.fn(),
    updateEvent: vi.fn(),
    deleteEvent: vi.fn(),
}));

const sampleEvents = [
    {
        id: 1, title: "Praia Limpa", location: "Matosinhos", startDate: "2027-01-01T10:00:00",
        status: "PUBLISHED", capacity: 20, subscriberCount: 5,
    },
    {
        id: 2, title: "Rascunho Interno", location: "Porto", startDate: "2027-02-01T10:00:00",
        status: "DRAFT", capacity: 10, subscriberCount: 0,
    },
    {
        id: 3, title: "Já a Decorrer", location: "Porto", startDate: "2020-01-01T10:00:00",
        status: "PUBLISHED", capacity: 15, subscriberCount: 3,
    },
    {
        id: 4, title: "Evento Encerrado", location: "Porto", startDate: "2019-01-01T10:00:00",
        status: "CLOSED", capacity: 5, subscriberCount: 5,
    },
    {
        id: 5, title: "Encerrado Antes do Fim", location: "Porto", startDate: "2020-01-01T10:00:00",
        endDate: "2027-01-01T10:00:00", status: "CLOSED", capacity: 5, subscriberCount: 2,
    },
    {
        id: 6, title: "Já Terminado", location: "Porto", startDate: "2019-01-01T10:00:00",
        endDate: "2019-01-01T12:00:00", status: "PUBLISHED", capacity: 5, subscriberCount: 1,
    },
];

function renderDashboard() {
    return render(
        <MemoryRouter>
            <OrganizerDashboard />
        </MemoryRouter>
    );
}

function actionsRowFor(title) {
    return screen.getByText(title).closest(".MuiCard-root");
}

describe("OrganizerDashboard", () => {
    beforeEach(() => {
        getMyEvents.mockReset();
        updateEvent.mockReset();
        deleteEvent.mockReset();
        getMyEvents.mockResolvedValue({ data: sampleEvents });
        vi.spyOn(window, "confirm").mockReturnValue(true);
    });

    it("renders the organizer's own events with subscriber counts", async () => {
        renderDashboard();

        expect(await screen.findByText("Praia Limpa")).toBeInTheDocument();
        expect(screen.getByText("Rascunho Interno")).toBeInTheDocument();
        expect(screen.getByText("5 / 20 inscritos")).toBeInTheDocument();
    });

    it("shows 'Cancelar' (not 'Encerrar') for a published event that hasn't started, and deletes it", async () => {
        const user = userEvent.setup();
        deleteEvent.mockResolvedValue({});
        renderDashboard();

        const card = await screen.findByText("Praia Limpa").then(() => actionsRowFor("Praia Limpa"));
        const { getByRole, queryByRole } = within(card);
        expect(queryByRole("button", { name: /encerrar/i })).not.toBeInTheDocument();
        await user.click(getByRole("button", { name: /cancelar/i }));

        await waitFor(() => expect(deleteEvent).toHaveBeenCalledWith(1));
    });

    it("shows 'Eliminar' for a draft and deletes it", async () => {
        const user = userEvent.setup();
        deleteEvent.mockResolvedValue({});
        renderDashboard();

        const card = await screen.findByText("Rascunho Interno").then(() => actionsRowFor("Rascunho Interno"));
        await user.click(within(card).getByRole("button", { name: /eliminar/i }));

        await waitFor(() => expect(deleteEvent).toHaveBeenCalledWith(2));
    });

    it("still shows 'Encerrar' (not 'Cancelar') for a published event that already started, and closes it", async () => {
        const user = userEvent.setup();
        updateEvent.mockResolvedValue({ data: { ...sampleEvents[2], status: "CLOSED" } });
        renderDashboard();

        const card = await screen.findByText("Já a Decorrer").then(() => actionsRowFor("Já a Decorrer"));
        const { getByRole, queryByRole } = within(card);
        expect(queryByRole("button", { name: /cancelar/i })).not.toBeInTheDocument();
        await user.click(getByRole("button", { name: /encerrar/i }));

        await waitFor(() => expect(updateEvent).toHaveBeenCalledWith(3, expect.objectContaining({ status: "CLOSED" })));
    });

    it("shows no destructive action for a closed event", async () => {
        renderDashboard();

        const card = await screen.findByText("Evento Encerrado").then(() => actionsRowFor("Evento Encerrado"));
        const { queryByRole } = within(card);
        expect(queryByRole("button", { name: /encerrar/i })).not.toBeInTheDocument();
        expect(queryByRole("button", { name: /cancelar/i })).not.toBeInTheDocument();
        expect(queryByRole("button", { name: /eliminar/i })).not.toBeInTheDocument();
    });

    it("still shows 'Editar' for a closed event whose endDate hasn't arrived yet (closed early)", async () => {
        renderDashboard();

        // Editar usa component={RouterLink}, por isso renderiza como <a> (role "link"), não "button".
        const card = await screen.findByText("Encerrado Antes do Fim").then(() => actionsRowFor("Encerrado Antes do Fim"));
        expect(within(card).getByRole("link", { name: /editar/i })).toBeInTheDocument();
    });

    it("hides 'Editar' once an event's endDate has actually passed, regardless of status", async () => {
        renderDashboard();

        const card = await screen.findByText("Já Terminado").then(() => actionsRowFor("Já Terminado"));
        expect(within(card).queryByRole("link", { name: /editar/i })).not.toBeInTheDocument();
    });
});
