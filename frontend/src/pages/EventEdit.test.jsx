import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import EventEdit from "./EventEdit";
import { getEvent, updateEvent } from "../api/event";

vi.mock("../api/event", () => ({
    getEvent: vi.fn(),
    updateEvent: vi.fn(),
    uploadEventImage: vi.fn(),
}));

const baseEvent = {
    id: 1, title: "Praia Limpa", location: "Matosinhos", description: "",
    startDate: "2027-01-01T10:00:00", endDate: "2027-01-01T12:00:00",
    imageUrl: null, capacity: 20, type: "AMBIENTE",
};

function renderPage() {
    return render(
        <MemoryRouter initialEntries={["/events/1/edit"]}>
            <Routes>
                <Route path="/events/:id/edit" element={<EventEdit />} />
            </Routes>
        </MemoryRouter>
    );
}

async function openStatusOptions(user) {
    const select = await screen.findByLabelText("Estado", { exact: false });
    await user.click(select);
    return screen.getByRole("listbox");
}

describe("EventEdit status dropdown", () => {
    beforeEach(() => {
        getEvent.mockReset();
        updateEvent.mockReset();
    });

    it("offers all three statuses when the event is currently a DRAFT", async () => {
        getEvent.mockResolvedValue({ data: { ...baseEvent, status: "DRAFT" } });
        const user = userEvent.setup();
        renderPage();

        const listbox = await openStatusOptions(user);
        expect(within(listbox).getAllByRole("option")).toHaveLength(3);
    });

    it("hides 'Rascunho' when the event is PUBLISHED and has subscribers", async () => {
        getEvent.mockResolvedValue({ data: { ...baseEvent, status: "PUBLISHED", subscriberCount: 3 } });
        const user = userEvent.setup();
        renderPage();

        const listbox = await openStatusOptions(user);
        const options = within(listbox).getAllByRole("option");
        expect(options).toHaveLength(2);
        expect(within(listbox).queryByText("Rascunho")).not.toBeInTheDocument();
    });

    it("still offers 'Rascunho' when the event is PUBLISHED but has no subscribers yet", async () => {
        getEvent.mockResolvedValue({ data: { ...baseEvent, status: "PUBLISHED", subscriberCount: 0 } });
        const user = userEvent.setup();
        renderPage();

        const listbox = await openStatusOptions(user);
        expect(within(listbox).getAllByRole("option")).toHaveLength(3);
    });

    it("disables the status field entirely when the event is CLOSED", async () => {
        getEvent.mockResolvedValue({ data: { ...baseEvent, status: "CLOSED" } });
        renderPage();

        const select = await screen.findByLabelText("Estado", { exact: false });
        expect(select).toHaveAttribute("aria-disabled", "true");
    });
});
