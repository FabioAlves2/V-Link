import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import EventList from "./EventList";
import { getEvents } from "../api/event";

vi.mock("../api/event", () => ({
  getEvents: vi.fn(),
}));

const sampleEvents = [
  { id: 1, title: "Praia Limpa", location: "Matosinhos", startDate: "2027-01-01T10:00:00", type: "AMBIENTE" },
  { id: 2, title: "Doação de Sangue", location: "Porto", startDate: "2027-02-01T10:00:00", type: "DOACAO" },
];

function renderEventList() {
  return render(
    <MemoryRouter>
      <EventList />
    </MemoryRouter>
  );
}

describe("EventList", () => {
  beforeEach(() => {
    getEvents.mockReset();
    getEvents.mockResolvedValue({ data: sampleEvents });
  });

  it("renders fetched events", async () => {
    renderEventList();

    expect(await screen.findByText("Praia Limpa")).toBeInTheDocument();
    expect(screen.getByText("Doação de Sangue")).toBeInTheDocument();
    expect(getEvents).toHaveBeenCalledTimes(1);
  });

  it("debounces filter changes instead of firing a request per keystroke", async () => {
    const user = userEvent.setup();
    renderEventList();
    await screen.findByText("Praia Limpa");
    getEvents.mockClear();

    await user.type(screen.getByLabelText("Local"), "Porto");

    // Right after typing, the 400ms debounce timer shouldn't have fired yet
    expect(getEvents).not.toHaveBeenCalled();

    await waitFor(() => expect(getEvents).toHaveBeenCalledTimes(1), { timeout: 1000 });
    expect(getEvents).toHaveBeenCalledWith(expect.objectContaining({ location: "Porto" }));
  });
});
