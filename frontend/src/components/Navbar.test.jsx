import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import Navbar from "./Navbar";
import { getNotifications, getUnreadCount, markNotificationRead } from "../api/notification";
import { useAuth } from "../context/authContext";

vi.mock("../api/notification", () => ({
    getNotifications: vi.fn(),
    getUnreadCount: vi.fn(),
    markNotificationRead: vi.fn(),
    markAllNotificationsRead: vi.fn(),
}));

vi.mock("../context/authContext", () => ({ useAuth: vi.fn() }));

const sampleNotifications = [
    { id: 1, message: 'O evento "Praia Limpa" foi encerrado.', read: false, eventId: 5 },
];

function renderNavbar() {
    return render(
        <MemoryRouter>
            <Navbar />
        </MemoryRouter>
    );
}

describe("Navbar notifications", () => {
    beforeEach(() => {
        getNotifications.mockReset();
        getUnreadCount.mockReset();
        markNotificationRead.mockReset();
        useAuth.mockReset();
        useAuth.mockReturnValue({ token: "fake-token", role: "VOLUNTEER", logout: vi.fn() });
        getUnreadCount.mockResolvedValue({ data: { count: 1 } });
        getNotifications.mockResolvedValue({ data: sampleNotifications });
    });

    it("shows the unread count badge", async () => {
        renderNavbar();
        expect(await screen.findByText("1")).toBeInTheDocument();
    });

    it("opens the dropdown and lists notifications on bell click", async () => {
        const user = userEvent.setup();
        renderNavbar();
        await screen.findByText("1");

        await user.click(screen.getByTestId("NotificationsNoneIcon").closest("button"));

        expect(await screen.findByText(/foi encerrado/)).toBeInTheDocument();
    });

    it("marks a notification as read when clicked", async () => {
        const user = userEvent.setup();
        markNotificationRead.mockResolvedValue({});
        renderNavbar();
        await screen.findByText("1");

        await user.click(screen.getByTestId("NotificationsNoneIcon").closest("button"));
        const notification = await screen.findByText(/foi encerrado/);
        await user.click(notification);

        await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith(1));
    });
});

describe("Navbar anonymous view", () => {
    beforeEach(() => {
        useAuth.mockReset();
        useAuth.mockReturnValue({ token: null, role: null, logout: vi.fn() });
    });

    it("renders a minimal logo + Entrar header when there is no token", () => {
        renderNavbar();

        expect(screen.getByText("V-Link")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /entrar/i })).toBeInTheDocument();
        expect(screen.queryByText("Eventos")).not.toBeInTheDocument();
        expect(screen.queryByText("As minhas inscrições")).not.toBeInTheDocument();
    });
});
