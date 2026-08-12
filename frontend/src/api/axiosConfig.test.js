import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("./auth", () => ({ refreshToken: vi.fn() }));

import api from "./axiosConfig";
import { refreshToken } from "./auth";

// Testa o interceptor de resposta diretamente (sem servidor mock) — técnica whitebox comum para
// axios: o handler "rejected" registado via interceptors.response.use fica acessível em
// interceptors.response.handlers[0].rejected.
const rejected = api.interceptors.response.handlers[0].rejected;

describe("axiosConfig response interceptor", () => {
    beforeEach(() => {
        localStorage.clear();
        refreshToken.mockReset();
    });

    it("rejects immediately without attempting a refresh when no refreshToken is stored", async () => {
        const originalRequest = { url: "/subscriptions/1", _retry: false, headers: {} };
        const error = { config: originalRequest, response: { status: 401 } };

        await expect(rejected(error)).rejects.toBe(error);
        expect(refreshToken).not.toHaveBeenCalled();
    });

    it("still attempts a refresh when a refreshToken IS stored and a genuine 401 occurs", async () => {
        localStorage.setItem("refreshToken", "some-refresh-token");
        refreshToken.mockResolvedValue({ data: { token: "new-token", refreshToken: "new-refresh" } });
        const originalRequest = { url: "/subscriptions/1", _retry: false, headers: {} };
        const error = { config: originalRequest, response: { status: 401 } };

        // A repetição do pedido original (api(originalRequest)) vai falhar sem backend real —
        // irrelevante para este teste, que só confirma que o refresh foi tentado.
        await rejected(error).catch(() => {});

        expect(refreshToken).toHaveBeenCalledTimes(1);
    });
});
