import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import EventSubscribers from "./EventSubscribers";
import { getEvent, getEventSubscribers, setAttendance } from "../api/event";

vi.mock("../api/event", () => ({
    getEvent: vi.fn(),
    getEventSubscribers: vi.fn(),
    setAttendance: vi.fn(),
}));

const sampleSubscribers = [
    { userId: 10, name: "Ana Voluntária", email: "ana@demo.pt", checkedIn: false },
    { userId: 11, name: "João Voluntário", email: "joao@demo.pt", checkedIn: true },
];

function renderPage() {
    return render(
        <MemoryRouter initialEntries={["/events/1/subscribers"]}>
            <Routes>
                <Route path="/events/:id/subscribers" element={<EventSubscribers />} />
            </Routes>
        </MemoryRouter>
    );
}

describe("EventSubscribers", () => {
    beforeEach(() => {
        getEvent.mockReset();
        getEventSubscribers.mockReset();
        setAttendance.mockReset();
        getEvent.mockResolvedValue({ data: { id: 1, title: "Praia Limpa" } });
        getEventSubscribers.mockResolvedValue({ data: sampleSubscribers });
    });

    it("renders the subscriber list for the event", async () => {
        renderPage();

        expect(await screen.findByText("Ana Voluntária")).toBeInTheDocument();
        expect(screen.getByText("João Voluntário")).toBeInTheDocument();
        expect(screen.getByText(/Praia Limpa/)).toBeInTheDocument();
    });

    it("toggles attendance when the checkbox is clicked", async () => {
        const user = userEvent.setup();
        setAttendance.mockResolvedValue({ data: { ...sampleSubscribers[0], checkedIn: true } });
        renderPage();

        await screen.findByText("Ana Voluntária");
        const checkboxes = screen.getAllByRole("checkbox");
        await user.click(checkboxes[0]);

        await waitFor(() => expect(setAttendance).toHaveBeenCalledWith("1", 10, true));
    });

    it("shows a forbidden message when the caller does not own the event", async () => {
        getEvent.mockRejectedValue({ response: { status: 403 } });
        getEventSubscribers.mockRejectedValue({ response: { status: 403 } });
        renderPage();

        expect(await screen.findByText(/só o promotor/i)).toBeInTheDocument();
    });
});
