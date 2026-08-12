import { useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route, useNavigate } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import Event from "./Event";
import { getEvent } from "../api/event";
import { isSubscribed, isFavorited, favoriteEvent, unfavoriteEvent } from "../api/user";
import { useAuth } from "../context/authContext";

vi.mock("../api/event", () => ({ getEvent: vi.fn() }));
vi.mock("../api/user", () => ({
    isSubscribed: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    isFavorited: vi.fn(),
    favoriteEvent: vi.fn(),
    unfavoriteEvent: vi.fn(),
}));
vi.mock("../context/authContext", () => ({ useAuth: vi.fn() }));

// Todos os testes existentes assumiam implicitamente sessão iniciada — Event.jsx agora chama
// useAuth(), por isso precisam de um valor por defeito aqui, senão rebentam com "Cannot
// destructure property 'token' of undefined". isFavorited só é chamado quando há token.
beforeEach(() => {
    useAuth.mockReset();
    useAuth.mockReturnValue({ token: "fake-token" });
    isFavorited.mockReset();
    isFavorited.mockResolvedValue({ data: { favorited: false } });
});

// Navega para outro evento pouco depois do primeiro montar, ainda com o pedido inicial pendente —
// simula o utilizador a mudar de página antes da resposta antiga chegar.
function NavigateAway({ to, delayMs }) {
    const navigate = useNavigate();
    useEffect(() => {
        const t = setTimeout(() => navigate(to), delayMs);
        return () => clearTimeout(t);
    }, [navigate, to, delayMs]);
    return null;
}

function renderWithLateNavigation() {
    return render(
        <MemoryRouter initialEntries={["/events/1"]}>
            <NavigateAway to="/events/2" delayMs={10} />
            <Routes>
                <Route path="/events/:id" element={<Event />} />
            </Routes>
        </MemoryRouter>
    );
}

describe("Event stale-response guard", () => {
    beforeEach(() => {
        getEvent.mockReset();
        isSubscribed.mockReset();
        isSubscribed.mockResolvedValue({ data: { subscribed: false } });
    });

    it("ignores a slow response for an id the user has already navigated away from", async () => {
        let resolveEvent1;
        getEvent.mockImplementation((id) => {
            if (id === "1") {
                return new Promise((resolve) => { resolveEvent1 = resolve; });
            }
            return Promise.resolve({
                data: { id: 2, title: "Evento 2", location: "Porto", capacity: 10, subscriberCount: 0, startDate: "2027-01-01T10:00:00" },
            });
        });

        renderWithLateNavigation();

        await waitFor(() => expect(screen.getByText("Evento 2")).toBeInTheDocument());

        // A resposta do evento 1 só chega agora, depois de já se estar na página do evento 2.
        resolveEvent1({
            data: { id: 1, title: "Evento 1 (obsoleto)", location: "Lisboa", capacity: 5, subscriberCount: 0, startDate: "2027-01-01T10:00:00" },
        });

        // Dá tempo à promise resolvida tardiamente para correr o seu .then — não deve alterar o ecrã.
        await new Promise((r) => setTimeout(r, 20));
        expect(screen.getByText("Evento 2")).toBeInTheDocument();
        expect(screen.queryByText("Evento 1 (obsoleto)")).not.toBeInTheDocument();
    });
});

function renderEvent() {
    return render(
        <MemoryRouter initialEntries={["/events/1"]}>
            <Routes>
                <Route path="/events/:id" element={<Event />} />
            </Routes>
        </MemoryRouter>
    );
}

const now = new Date();
const hoursFromNow = (h) => new Date(now.getTime() + h * 3600 * 1000).toISOString();

describe("Event subscribe button availability", () => {
    beforeEach(() => {
        getEvent.mockReset();
        isSubscribed.mockReset();
    });

    it("shows the actionable button for an ongoing PUBLISHED event", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "A Decorrer", location: "Porto", capacity: 10, subscriberCount: 0,
                status: "PUBLISHED", startDate: hoursFromNow(-1), endDate: hoursFromNow(2) },
        });
        isSubscribed.mockResolvedValue({ data: { subscribed: false } });
        renderEvent();

        expect(await screen.findByRole("button", { name: /inscrever-me neste evento/i })).toBeEnabled();
    });

    it("shows an info alert instead of a button for a PUBLISHED event past its endDate, not subscribed", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "Já Terminado", location: "Porto", capacity: 10, subscriberCount: 3,
                status: "PUBLISHED", startDate: hoursFromNow(-4), endDate: hoursFromNow(-2) },
        });
        isSubscribed.mockResolvedValue({ data: { subscribed: false } });
        renderEvent();

        expect(await screen.findByText("Este evento já terminou.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /inscrever-me/i })).not.toBeInTheDocument();
    });

    it("shows a disabled 'Inscrito' badge for a past event the volunteer is already subscribed to", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "Já Terminado", location: "Porto", capacity: 10, subscriberCount: 3,
                status: "PUBLISHED", startDate: hoursFromNow(-4), endDate: hoursFromNow(-2) },
        });
        isSubscribed.mockResolvedValue({ data: { subscribed: true } });
        renderEvent();

        const badge = await screen.findByRole("button", { name: /inscrito.*terminou/i });
        expect(badge).toBeDisabled();
    });

    it("shows an info alert for a CLOSED event", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "Encerrado", location: "Porto", capacity: 10, subscriberCount: 3,
                status: "CLOSED", startDate: hoursFromNow(-4), endDate: hoursFromNow(2) },
        });
        isSubscribed.mockResolvedValue({ data: { subscribed: false } });
        renderEvent();

        expect(await screen.findByText("Este evento já foi encerrado.")).toBeInTheDocument();
    });
});

describe("Event anonymous access", () => {
    beforeEach(() => {
        getEvent.mockReset();
        isSubscribed.mockReset();
        isFavorited.mockReset();
        useAuth.mockReturnValue({ token: null });
    });

    it("renders event details for an anonymous visitor without calling isSubscribed/isFavorited", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "A Decorrer", location: "Porto", capacity: 10, subscriberCount: 0,
                status: "PUBLISHED", startDate: hoursFromNow(-1), endDate: hoursFromNow(2) },
        });
        renderEvent();

        expect(await screen.findByText("A Decorrer")).toBeInTheDocument();
        expect(isSubscribed).not.toHaveBeenCalled();
        expect(isFavorited).not.toHaveBeenCalled();
    });

    it("shows a sign-in CTA instead of the subscribe button when anonymous", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "A Decorrer", location: "Porto", capacity: 10, subscriberCount: 0,
                status: "PUBLISHED", startDate: hoursFromNow(-1), endDate: hoursFromNow(2) },
        });
        renderEvent();

        expect(await screen.findByRole("button", { name: /inicia sessão para te inscreveres/i })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /inscrever-me neste evento/i })).not.toBeInTheDocument();
    });

    it("clicking the sign-in CTA navigates to /login", async () => {
        const user = userEvent.setup();
        getEvent.mockResolvedValue({
            data: { id: 1, title: "A Decorrer", location: "Porto", capacity: 10, subscriberCount: 0,
                status: "PUBLISHED", startDate: hoursFromNow(-1), endDate: hoursFromNow(2) },
        });
        render(
            <MemoryRouter initialEntries={["/events/1"]}>
                <Routes>
                    <Route path="/events/:id" element={<Event />} />
                    <Route path="/login" element={<div>Página de login</div>} />
                </Routes>
            </MemoryRouter>
        );

        await user.click(await screen.findByRole("button", { name: /inicia sessão para te inscreveres/i }));

        expect(await screen.findByText("Página de login")).toBeInTheDocument();
    });

    it("shows the same 'this event has ended' info alert for an anonymous visitor as for a logged-in one", async () => {
        getEvent.mockResolvedValue({
            data: { id: 1, title: "Já Terminado", location: "Porto", capacity: 10, subscriberCount: 3,
                status: "PUBLISHED", startDate: hoursFromNow(-4), endDate: hoursFromNow(-2) },
        });
        renderEvent();

        expect(await screen.findByText("Este evento já terminou.")).toBeInTheDocument();
    });
});

describe("Event favorites", () => {
    beforeEach(() => {
        getEvent.mockReset();
        isSubscribed.mockReset();
        isSubscribed.mockResolvedValue({ data: { subscribed: false } });
        favoriteEvent.mockReset();
        unfavoriteEvent.mockReset();
        getEvent.mockResolvedValue({
            data: { id: 1, title: "A Decorrer", location: "Porto", capacity: 10, subscriberCount: 0,
                status: "PUBLISHED", startDate: hoursFromNow(-1), endDate: hoursFromNow(2) },
        });
    });

    it("shows an outlined heart when the event is not favorited and a filled heart when it is", async () => {
        isFavorited.mockResolvedValue({ data: { favorited: true } });
        renderEvent();

        expect(await screen.findByLabelText("Remover dos favoritos")).toBeInTheDocument();
    });

    it("toggles favorite state on click", async () => {
        const user = userEvent.setup();
        isFavorited.mockResolvedValue({ data: { favorited: false } });
        favoriteEvent.mockResolvedValue({ data: { favorited: true } });
        renderEvent();

        const heart = await screen.findByLabelText("Adicionar aos favoritos");
        await user.click(heart);

        await waitFor(() => expect(favoriteEvent).toHaveBeenCalledWith("1"));
        expect(await screen.findByLabelText("Remover dos favoritos")).toBeInTheDocument();
    });

    it("shows the sign-in prompt instead of calling the API when clicked anonymously", async () => {
        const user = userEvent.setup();
        useAuth.mockReturnValue({ token: null });
        isFavorited.mockResolvedValue({ data: { favorited: false } });
        render(
            <MemoryRouter initialEntries={["/events/1"]}>
                <Routes>
                    <Route path="/events/:id" element={<Event />} />
                    <Route path="/login" element={<div>Página de login</div>} />
                </Routes>
            </MemoryRouter>
        );

        const heart = await screen.findByLabelText("Adicionar aos favoritos");
        await user.click(heart);

        expect(await screen.findByText("Página de login")).toBeInTheDocument();
        expect(favoriteEvent).not.toHaveBeenCalled();
    });
});
