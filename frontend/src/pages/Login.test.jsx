import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import Login from "./Login";
import { AuthProvider } from "../context/authContext";
import { login } from "../api/auth";

vi.mock("../api/auth", () => ({
  login: vi.fn(),
  logout: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("Login", () => {
  beforeEach(() => {
    login.mockReset();
    mockNavigate.mockReset();
  });

  it("logs in and navigates to /events on valid credentials", async () => {
    login.mockResolvedValue({ data: { token: "access-token", refreshToken: "refresh-token" } });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Email", { exact: false }), "user@example.com");
    await user.type(screen.getByLabelText("Password", { exact: false }), "password123");
    await user.click(screen.getByRole("button", { name: /entrar/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/events"));
    expect(login).toHaveBeenCalledWith({ email: "user@example.com", password: "password123" });
  });

  it("shows the server error message on invalid credentials", async () => {
    login.mockRejectedValue({ response: { data: { error: "Credenciais inválidas." } } });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Email", { exact: false }), "user@example.com");
    await user.type(screen.getByLabelText("Password", { exact: false }), "wrongpassword");
    await user.click(screen.getByRole("button", { name: /entrar/i }));

    expect(await screen.findByText("Credenciais inválidas.")).toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
