import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import MySubscriptions from "./MySubscriptions";
import api from "../api/axiosConfig";

vi.mock("../api/axiosConfig", () => ({
    default: { get: vi.fn(), delete: vi.fn() },
}));

const now = new Date();
const hoursFromNow = (h) => new Date(now.getTime() + h * 3600 * 1000).toISOString();

const sampleEvents = [
    {
        id: 1, title: "Evento a decorrer", location: "Porto",
        startDate: hoursFromNow(-1), endDate: hoursFromNow(2),
        status: "PUBLISHED",
    },
    {
        id: 2, title: "Evento encerrado antes do fim", location: "Porto",
        // endDate ainda no futuro de propósito: encerrar só depende do startDate já ter
        // passado, não do endDate — este é exatamente o caso que a lógica só-por-datas falhava.
        startDate: hoursFromNow(-2), endDate: hoursFromNow(5),
        status: "CLOSED",
    },
];

function renderPage() {
    return render(
        <MemoryRouter>
            <MySubscriptions />
        </MemoryRouter>
    );
}

function cardFor(title) {
    return screen.getByText(title).closest(".MuiCard-root");
}

describe("MySubscriptions", () => {
    beforeEach(() => {
        api.get.mockReset();
        api.delete.mockReset();
        api.get.mockResolvedValue({ data: sampleEvents });
    });

    it("labels a closed event as 'encerrado' and hides the Cancelar button, even with a future endDate", async () => {
        renderPage();

        const card = await screen.findByText("Evento encerrado antes do fim").then(() => cardFor("Evento encerrado antes do fim"));
        expect(within(card).getByText("encerrado")).toBeInTheDocument();
        expect(within(card).queryByRole("button", { name: /cancelar/i })).not.toBeInTheDocument();
    });

    it("still shows Cancelar for an ongoing (not closed) subscription and calls the API", async () => {
        api.delete.mockResolvedValue({});
        const user = userEvent.setup();
        renderPage();

        const card = await screen.findByText("Evento a decorrer").then(() => cardFor("Evento a decorrer"));
        await user.click(within(card).getByRole("button", { name: /cancelar/i }));

        await waitFor(() => expect(api.delete).toHaveBeenCalledWith("/subscriptions/1"));
    });

    it("surfaces the backend's error message instead of a generic one", async () => {
        api.delete.mockRejectedValue({ response: { data: { error: "Não é possível cancelar a inscrição de um evento já encerrado." } } });
        const user = userEvent.setup();
        renderPage();

        const card = await screen.findByText("Evento a decorrer").then(() => cardFor("Evento a decorrer"));
        await user.click(within(card).getByRole("button", { name: /cancelar/i }));

        expect(await screen.findByText("Não é possível cancelar a inscrição de um evento já encerrado.")).toBeInTheDocument();
    });
});
